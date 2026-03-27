/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bloomberg.datalake.trino.plugin.externalhttpgroups;

import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.trino.spi.security.GroupProvider;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.bloomberg.datalake.trino.plugin.externalhttpgroups.ExternalHttpGroupsGroupProvider.encodeBase64;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

/**
 * System tests to verify retry behavior when server connections fail prematurely.
 *
 * These tests simulate the PrematureCloseException scenario where the server
 * closes the connection before sending response data, which can happen when:
 * - Server keep-alive timeout expires before client reuses connection
 * - Network interruptions occur mid-request
 * - Server crashes or restarts during request processing
 */
@TestInstance(PER_CLASS)
class TestExternalHttpGroupsRetry
{
    private MockWebServer server;

    @BeforeEach
    public void setUp()
            throws IOException
    {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    public void tearDown()
            throws IOException
    {
        server.shutdown();
    }

    @Test
    public void testRetryOnConnectionFailure()
            throws InterruptedException
    {
        // GIVEN - Server that fails first request, then succeeds
        server.enqueue(new MockResponse()
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_REQUEST_BODY));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"groupList\": [\"GROUP_A\", \"GROUP_B\"]}"));

        Map<String, String> config = Map.of(
                "externalhttpgroups.uri", server.url("/api/v1/groupprovider").toString(),
                "externalhttpgroups.cache-ttl", "10m");

        try (JettyHttpClient httpClient = new JettyHttpClient(new HttpClientConfig())) {
            GroupProvider groupProvider = new ExternalHttpGroupsGroupProviderFactory()
                    .create(config, Optional.of(httpClient));

            // WHEN - Request groups for user
            Set<String> groups = groupProvider.getGroups("testuser");

            // THEN - Should succeed after retry
            assertThat(groups).containsExactlyInAnyOrder("GROUP_A", "GROUP_B");

            // AND - Should have made exactly 2 requests (1 failed + 1 retry succeeded)
            RecordedRequest request1 = server.takeRequest(5, TimeUnit.SECONDS);
            RecordedRequest request2 = server.takeRequest(5, TimeUnit.SECONDS);

            assertThat(request1).isNotNull();
            assertThat(request1.getPath()).isEqualTo("/api/v1/groupprovider/getGroups/" + encodeBase64("testuser"));

            assertThat(request2).isNotNull();
            assertThat(request2.getPath()).isEqualTo("/api/v1/groupprovider/getGroups/" + encodeBase64("testuser"));

            // Verify no more requests were made
            assertThat(server.getRequestCount()).isEqualTo(2);
        }
    }

    @Test
    public void testRetryOnDisconnectDuringResponse()
            throws InterruptedException
    {
        // GIVEN - Server that disconnects while sending response body
        server.enqueue(new MockResponse()
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"groupList\": [\"GROUP_A\", \"GROUP_B\"]}"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"groupList\": [\"GROUP_A\", \"GROUP_B\"]}"));

        Map<String, String> config = Map.of(
                "externalhttpgroups.uri", server.url("/api/v1/groupprovider").toString(),
                "externalhttpgroups.cache-ttl", "10m");

        HttpClientConfig httpConfig = new HttpClientConfig();
        try (JettyHttpClient httpClient = new JettyHttpClient(httpConfig)) {
            GroupProvider groupProvider = new ExternalHttpGroupsGroupProviderFactory()
                    .create(config, Optional.of(httpClient));

            // WHEN - Request groups for user
            Set<String> groups = groupProvider.getGroups("testuser");

            // THEN - Should succeed after retry
            assertThat(groups).containsExactlyInAnyOrder("GROUP_A", "GROUP_B");

            // AND - Should have made exactly 2 requests
            assertThat(server.getRequestCount()).isEqualTo(2);
        }
    }

    @Test
    public void testRetriesExhaustedReturnsEmptySet()
            throws InterruptedException
    {
        // GIVEN - Server that always fails (more failures than max retries)
        for (int i = 0; i < 5; i++) {
            server.enqueue(new MockResponse()
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_REQUEST_BODY));
        }

        Map<String, String> config = Map.of(
                "externalhttpgroups.uri", server.url("/api/v1/groupprovider").toString(),
                "externalhttpgroups.cache-ttl", "10m");

        HttpClientConfig httpConfig = new HttpClientConfig();
        try (JettyHttpClient httpClient = new JettyHttpClient(httpConfig)) {
            GroupProvider groupProvider = new ExternalHttpGroupsGroupProviderFactory()
                    .create(config, Optional.of(httpClient));

            // WHEN - Request groups for user
            Set<String> groups = groupProvider.getGroups("testuser");

            // THEN - Should return empty set after exhausting retries
            assertThat(groups).isEmpty();

            // AND - Should have made max retry attempts (3 attempts by default)
            assertThat(server.getRequestCount()).isEqualTo(3);

            // Verify all requests went to correct endpoint
            for (int i = 0; i < 3; i++) {
                RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
                assertThat(request).isNotNull();
                assertThat(request.getPath()).isEqualTo("/api/v1/groupprovider/getGroups/" + encodeBase64("testuser"));
            }
        }
    }

    @Test
    public void testNoRetryAfterSuccessfulResponse()
            throws InterruptedException
    {
        // GIVEN - Server that succeeds immediately
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"groupList\": [\"GROUP_A\"]}"));

        Map<String, String> config = Map.of(
                "externalhttpgroups.uri", server.url("/api/v1/groupprovider").toString(),
                "externalhttpgroups.cache-ttl", "10m");

        HttpClientConfig httpConfig = new HttpClientConfig();
        try (JettyHttpClient httpClient = new JettyHttpClient(httpConfig)) {
            GroupProvider groupProvider = new ExternalHttpGroupsGroupProviderFactory()
                    .create(config, Optional.of(httpClient));

            // WHEN - Request groups for user
            Set<String> groups = groupProvider.getGroups("testuser");

            // THEN - Should succeed
            assertThat(groups).containsExactly("GROUP_A");

            // AND - Should have made only 1 request (no retries needed)
            assertThat(server.getRequestCount()).isEqualTo(1);

            RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/api/v1/groupprovider/getGroups/" + encodeBase64("testuser"));
        }
    }

    @Test
    public void testRetryOnServerError()
    {
        // GIVEN - Server that returns 500 error first, then succeeds
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\": \"Internal server error\"}"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"groupList\": [\"GROUP_A\", \"GROUP_B\"]}"));

        Map<String, String> config = Map.of(
                "externalhttpgroups.uri", server.url("/api/v1/groupprovider").toString(),
                "externalhttpgroups.cache-ttl", "10m");

        HttpClientConfig httpConfig = new HttpClientConfig();
        try (JettyHttpClient httpClient = new JettyHttpClient(httpConfig)) {
            GroupProvider groupProvider = new ExternalHttpGroupsGroupProviderFactory()
                    .create(config, Optional.of(httpClient));

            // WHEN - Request groups for user
            Set<String> groups = groupProvider.getGroups("testuser");

            // THEN - Should succeed after retry
            assertThat(groups).containsExactlyInAnyOrder("GROUP_A", "GROUP_B");

            // AND - Should have made exactly 2 requests
            assertThat(server.getRequestCount()).isEqualTo(2);
        }
    }
}

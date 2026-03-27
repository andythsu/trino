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
package com.bloomberg.datalake.trino.plugin;

import com.bloomberg.datalake.bpi.DatalakeLDAPBloombergPrincipal;
import com.bloomberg.datalake.bpi.DatalakeWAGPrincipal;
import com.github.benmanes.caffeine.cache.Ticker;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.json.JsonCodec;
import io.trino.spi.security.BasicPrincipal;
import io.trino.spi.security.UserAttributeProvider;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.bloomberg.datalake.trino.plugin.DatalakeUserAttributeProvider.buildPrincipalInfo;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(Lifecycle.PER_CLASS)
class TestDatalakeUserAttributeProviderHttp
{
    private static final JsonCodec<UserAttributeProviderRequest> REQUEST_CODEC = JsonCodec.jsonCodec(UserAttributeProviderRequest.class);

    @Test
    public void testHttpDisabledBackwardCompatibility()
            throws Exception
    {
        // No http.uri configured — should return empty map (no BAS either since no bas.host)
        Map<String, String> config = Map.of();

        try (JettyHttpClient httpClient = new JettyHttpClient()) {
            UserAttributeProvider provider = new DatalakeUserAttributeProviderFactory()
                    .create(config, Optional.of(httpClient));

            Map<String, Object> attributes = provider.getUserAttributes("testuser", Optional.empty());
            assertThat(attributes).isEmpty();
        }
    }

    @Test
    public void testHttpOnlyWithLdapPrincipal()
            throws Exception
    {
        AtomicInteger requestCount = new AtomicInteger(0);

        try (UserAttributeTestServer testServer = new UserAttributeTestServer(
                countingServlet(requestCount, "{\"attributes\": {\"department\": \"engineering\"}}"))) {
            UserAttributeProvider provider = testServer.createAttributeProvider();

            DatalakeLDAPBloombergPrincipal ldapPrincipal = new DatalakeLDAPBloombergPrincipal("ldapuser", Optional.empty());
            Map<String, Object> attributes = provider.getUserAttributes("ldapuser", Optional.of(ldapPrincipal));

            assertThat(attributes).containsEntry("department", "engineering");
            assertThat(requestCount.get()).isEqualTo(1);
        }
    }

    @Test
    public void testHttpFailureGracefulDegradation()
            throws Exception
    {
        AtomicInteger requestCount = new AtomicInteger(0);

        // Servlet that always fails (more failures than retry attempts)
        try (UserAttributeTestServer testServer = new UserAttributeTestServer(
                failThenSucceedServlet(requestCount, Integer.MAX_VALUE, "{\"attributes\": {}}"))) {
            UserAttributeProvider provider = testServer.createAttributeProvider();

            // HTTP fails — should return empty map gracefully, no exception
            Map<String, Object> attributes = provider.getUserAttributes("testuser", Optional.empty());
            assertThat(attributes).isEmpty();
            assertThat(requestCount.get()).isEqualTo(3); // 3 retry attempts
        }
    }

    @Test
    public void testHttpCachingResult()
            throws Exception
    {
        AtomicInteger requestCount = new AtomicInteger(0);

        try (UserAttributeTestServer testServer = new UserAttributeTestServer(
                countingServlet(requestCount, "{\"attributes\": {\"cached_key\": \"cached_value\"}}"))) {
            UserAttributeProvider provider = testServer.createAttributeProvider();

            // First call — should hit the servlet
            Map<String, Object> attributes1 = provider.getUserAttributes("testuser", Optional.empty());
            assertThat(attributes1).containsEntry("cached_key", "cached_value");
            assertThat(requestCount.get()).isEqualTo(1);

            // Second call with the same user — should be served from cache
            Map<String, Object> attributes2 = provider.getUserAttributes("testuser", Optional.empty());
            assertThat(attributes2).containsEntry("cached_key", "cached_value");
            assertThat(requestCount.get()).isEqualTo(1);
        }
    }

    @Test
    public void testCacheEvictsAfterTtlExpiry()
            throws Exception
    {
        AtomicInteger requestCount = new AtomicInteger(0);
        FakeTicker ticker = new FakeTicker();

        try (UserAttributeTestServer testServer = new UserAttributeTestServer(
                countingServlet(requestCount, "{\"attributes\": {\"key\": \"value\"}}"))) {
            UserAttributeProvider provider = testServer.createAttributeProvider("1s", ticker);

            // First call — should hit the servlet
            Map<String, Object> attributes = provider.getUserAttributes("testuser", Optional.empty());
            assertThat(attributes).containsEntry("key", "value");
            assertThat(requestCount.get()).isEqualTo(1);

            // Second call — should be served from cache
            provider.getUserAttributes("testuser", Optional.empty());
            assertThat(requestCount.get()).isEqualTo(1);

            // Advance time past TTL
            ticker.advance(Duration.ofSeconds(2));

            // Third call — cache entry expired, should hit the servlet again
            attributes = provider.getUserAttributes("testuser", Optional.empty());
            assertThat(attributes).containsEntry("key", "value");
            assertThat(requestCount.get()).isEqualTo(2);
        }
    }

    @Test
    public void testFailedLoadIsNotCached()
            throws Exception
    {
        AtomicInteger requestCount = new AtomicInteger(0);

        try (UserAttributeTestServer testServer = new UserAttributeTestServer(
                failThenSucceedServlet(requestCount, 3, "{\"attributes\": {\"key\": \"value\"}}"))) {
            UserAttributeProvider provider = testServer.createAttributeProvider();

            // First call — all 3 retry attempts fail, should return empty map
            Map<String, Object> attributes = provider.getUserAttributes("testuser", Optional.empty());
            assertThat(attributes).isEmpty();
            assertThat(requestCount.get()).isEqualTo(3);

            // Second call — failure should NOT be cached, loader should run again and succeed
            attributes = provider.getUserAttributes("testuser", Optional.empty());
            assertThat(attributes).containsEntry("key", "value");
            assertThat(requestCount.get()).isEqualTo(4);
        }
    }

    @Test
    public void testConcurrentAccessForSameUserMakesSingleHttpCall()
            throws Exception
    {
        AtomicInteger requestCount = new AtomicInteger(0);

        try (UserAttributeTestServer testServer = new UserAttributeTestServer(
                countingServlet(requestCount, "{\"attributes\": {\"key\": \"value\"}}"))) {
            UserAttributeProvider provider = testServer.createAttributeProvider();

            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            try {
                for (int i = 0; i < threadCount; i++) {
                    executor.submit(() -> {
                        try {
                            startLatch.await();
                            Map<String, Object> attributes = provider.getUserAttributes("testuser", Optional.empty());
                            assertThat(attributes).containsEntry("key", "value");
                        }
                        catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        finally {
                            doneLatch.countDown();
                        }
                    });
                }

                // Release all threads simultaneously
                startLatch.countDown();
                assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
            }
            finally {
                executor.shutdown();
            }

            // Cache should coalesce concurrent loads — only 1 HTTP call for the same user
            assertThat(requestCount.get()).isEqualTo(1);
        }
    }

    @Test
    public void testPostRequestContainsUserInBody()
            throws Exception
    {
        AtomicReference<String> capturedRequestBody = new AtomicReference<>();

        try (UserAttributeTestServer testServer = new UserAttributeTestServer(
                capturingServlet(capturedRequestBody, "{\"attributes\": {\"key\": \"value\"}}"))) {
            UserAttributeProvider provider = testServer.createAttributeProvider();

            provider.getUserAttributes("testuser", Optional.empty());

            UserAttributeProviderRequest parsed = REQUEST_CODEC.fromJson(capturedRequestBody.get());
            assertThat(parsed.user()).isEqualTo("testuser");
            assertThat(parsed.principal()).isNull();
        }
    }

    @Test
    public void testPostRequestContainsPrincipalInfo()
            throws Exception
    {
        AtomicReference<String> capturedRequestBody = new AtomicReference<>();

        try (UserAttributeTestServer testServer = new UserAttributeTestServer(
                capturingServlet(capturedRequestBody, "{\"attributes\": {\"key\": \"value\"}}"))) {
            UserAttributeProvider provider = testServer.createAttributeProvider();

            DatalakeLDAPBloombergPrincipal ldapPrincipal = new DatalakeLDAPBloombergPrincipal("testuser", Optional.empty());
            provider.getUserAttributes("testuser", Optional.of(ldapPrincipal));

            UserAttributeProviderRequest parsed = REQUEST_CODEC.fromJson(capturedRequestBody.get());
            assertThat(parsed.user()).isEqualTo("testuser");
            assertThat(parsed.principal()).isNotNull();
            assertThat(parsed.principal().authMethod()).isEqualTo("BASIC");
            assertThat(parsed.principal().sessionId()).isNull();
        }
    }

    @Test
    public void testBuildPrincipalInfoForWAG()
    {
        DatalakeWAGPrincipal principal = DatalakeWAGPrincipal.builder()
                .primaryIdType("wag")
                .primaryId("testuser")
                .applicationFirm(1)
                .uuid(Optional.of(123L))
                .username(Optional.of("testuser"))
                .sessionId(Optional.of("wag-session-456"))
                .build();
        UserAttributeProviderRequest.PrincipalInfo info = buildPrincipalInfo(Optional.of(principal));

        assertThat(info.authMethod()).isEqualTo("WAG");
        assertThat(info.sessionId()).isEqualTo("wag-session-456");
    }

    @Test
    public void testBuildPrincipalInfoForLDAP()
    {
        DatalakeLDAPBloombergPrincipal principal = new DatalakeLDAPBloombergPrincipal("user", Optional.empty());
        UserAttributeProviderRequest.PrincipalInfo info = buildPrincipalInfo(Optional.of(principal));

        assertThat(info.authMethod()).isEqualTo("BASIC");
        assertThat(info.sessionId()).isNull();
    }

    @Test
    public void testBuildPrincipalInfoForNoPrincipal()
    {
        UserAttributeProviderRequest.PrincipalInfo info = buildPrincipalInfo(Optional.empty());

        assertThat(info).isNull();
    }

    @Test
    public void testBuildPrincipalInfoForUnknownPrincipal()
    {
        BasicPrincipal principal = new BasicPrincipal("someuser");
        UserAttributeProviderRequest.PrincipalInfo info = buildPrincipalInfo(Optional.of(principal));

        assertThat(info.authMethod()).isNull();
        assertThat(info.sessionId()).isNull();
    }

    /**
     * Creates a servlet that captures the request body from the POST request.
     */
    private static HttpServlet capturingServlet(AtomicReference<String> capturedRequestBody, String jsonResponse)
    {
        return new HttpServlet()
        {
            @Override
            protected void doPost(HttpServletRequest request, HttpServletResponse response)
                    throws IOException
            {
                capturedRequestBody.set(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8));

                response.setContentType("application/json");
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().println(jsonResponse);
            }
        };
    }

    /**
     * Creates a servlet that counts incoming requests.
     */
    private static HttpServlet countingServlet(AtomicInteger requestCount, String jsonResponse)
    {
        return new HttpServlet()
        {
            @Override
            protected void doPost(HttpServletRequest request, HttpServletResponse response)
                    throws IOException
            {
                requestCount.incrementAndGet();

                response.setContentType("application/json");
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().println(jsonResponse);
            }
        };
    }

    /**
     * Creates a servlet that returns HTTP 500 for the first {@code failForFirstN} requests,
     * then returns a successful response with the given JSON body.
     */
    private static HttpServlet failThenSucceedServlet(AtomicInteger requestCount, int failForFirstN, String successResponse)
    {
        return new HttpServlet()
        {
            @Override
            protected void doPost(HttpServletRequest request, HttpServletResponse response)
                    throws IOException
            {
                int count = requestCount.incrementAndGet();
                if (count <= failForFirstN) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    return;
                }
                response.setContentType("application/json");
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().println(successResponse);
            }
        };
    }

    /**
     * AutoCloseable wrapper around a Jetty server that serves as a user attribute provider
     * HTTP endpoint. Use with try-with-resources to ensure the server is stopped.
     */
    private static class UserAttributeTestServer
            implements AutoCloseable
    {
        private final Server server;
        private final int port;
        private final JettyHttpClient httpClient;

        UserAttributeTestServer(HttpServlet servlet)
                throws Exception
        {
            server = new Server();
            ServerConnector connector = new ServerConnector(server);
            connector.setPort(0);
            server.addConnector(connector);

            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            context.setContextPath("/");
            server.setHandler(context);
            context.addServlet(new ServletHolder(servlet), "/api/v1/attributeprovider/fetchUserAttributes");

            server.start();
            port = connector.getLocalPort();
            httpClient = new JettyHttpClient();
        }

        UserAttributeProvider createAttributeProvider()
        {
            return createAttributeProvider("10m");
        }

        UserAttributeProvider createAttributeProvider(String cacheTtl)
        {
            return createAttributeProvider(cacheTtl, Ticker.systemTicker());
        }

        UserAttributeProvider createAttributeProvider(String cacheTtl, Ticker ticker)
        {
            Map<String, String> config = Map.of(
                    "ses-bas.enabled", "false",
                    "http.uri", "http://localhost:" + port + "/api/v1/attributeprovider",
                    "http.cache-ttl", cacheTtl);

            return new DatalakeUserAttributeProviderFactory()
                    .create(config, Optional.of(httpClient), Optional.of(ticker));
        }

        @Override
        public void close()
                throws Exception
        {
            httpClient.close();
            server.stop();
        }
    }

    private static class FakeTicker
            implements Ticker
    {
        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long read()
        {
            return nanos.get();
        }

        void advance(Duration duration)
        {
            nanos.addAndGet(duration.toNanos());
        }
    }
}

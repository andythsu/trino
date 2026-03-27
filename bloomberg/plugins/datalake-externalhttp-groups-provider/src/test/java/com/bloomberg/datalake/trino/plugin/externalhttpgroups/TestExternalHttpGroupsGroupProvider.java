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

import com.bloomberg.datalake.bpi.DatalakeLDAPBloombergPrincipal;
import com.bloomberg.datalake.bpi.DatalakeWAGPrincipal;
import com.google.common.net.MediaType;
import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.client.testing.TestingHttpClient;
import io.trino.spi.security.GroupProvider;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.bloomberg.datalake.trino.plugin.externalhttpgroups.ExternalHttpGroupsGroupProvider.encodeBase64;
import static io.airlift.http.client.testing.TestingResponse.mockResponse;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ExternalHttpGroupsGroupProvider}.
 * Uses mocked HTTP client to verify group retrieval and error handling.
 */
@TestInstance(Lifecycle.PER_CLASS)
class TestExternalHttpGroupsGroupProvider
{
    @Test
    public void testEncodeBase64IncludesPadding()
    {
        assertThat(encodeBase64("agupta157")).isEqualTo("YWd1cHRhMTU3");
        assertThat(encodeBase64("jdoe")).isEqualTo("amRvZQ==");
    }

    @Test
    public void testGetGroups()
    {
        Map<String, String> config = Map.of(
                "externalhttpgroups.uri", "https://testurl.com/api/v1/groupprovider",
                "externalhttpgroups.cache-ttl", "10m");

        // Create TestingHttpClient that returns JSON responses based on the request URI
        TestingHttpClient httpClient = new TestingHttpClient(request -> {
            String uri = request.getUri().toString();

            String jsonResponse = switch (uri) {
                case String u when u.contains(encodeBase64("agupta157")) -> "{\"groupList\": [\"PVFX_ZOMO_NRPTFLDB\", \"PVFX_ZOMO_NRPTDB\"]}";
                case String u when u.contains(encodeBase64("some_other_person")) -> "{\"groupList\": [\"PVFX_ZOMO_XYZ\"]}";
                default -> "{\"groupList\": []}";
            };

            return mockResponse(
                HttpStatus.OK,
                MediaType.JSON_UTF_8,
                jsonResponse);
        });

        GroupProvider groupProvider = new ExternalHttpGroupsGroupProviderFactory()
                .create(config, Optional.of(httpClient));

        // Test first user
        Set<String> returnValue = groupProvider.getGroups("agupta157");
        Set<String> expected = Set.of("PVFX_ZOMO_NRPTFLDB", "PVFX_ZOMO_NRPTDB");
        assertThat(returnValue).isEqualTo(expected);

        // Test second user (cache should work across requests)
        returnValue = groupProvider.getGroups("some_other_person");
        expected = Set.of("PVFX_ZOMO_XYZ");
        assertThat(returnValue).isEqualTo(expected);
    }

    @Test
    public void testGetGroupsExtractsUuidFromWagPrincipalName()
            throws Exception
    {
        // Build a DatalakeWAGPrincipal the same way BloombergWagAuthenticator would
        DatalakeWAGPrincipal principal = DatalakeWAGPrincipal.builder()
                .primaryIdType("wag")
                .primaryId("11111")
                .applicationFirm(9001)
                .uuid(Optional.of(123L))
                .username(Optional.of("jdoe"))
                .sessionId(Optional.empty())
                .build();

        // This is the full serialized BPI string that Trino uses as the username
        String principalName = principal.getName();
        assertThat(principalName).isEqualTo("bpi:wag:bb_firm:9001:bb_uuid:123:primary_id:11111:primary_id_type:wag");

        // Capture the raw base64-encoded username that the servlet receives
        AtomicReference<String> receivedBase64Username = new AtomicReference<>();

        try (GroupProviderTestServer testServer = new GroupProviderTestServer(
                capturingServlet(receivedBase64Username, "{\"groupList\": [\"GROUP_A\", \"GROUP_B\"]}"))) {
            GroupProvider groupProvider = testServer.createGroupProvider();

            // Call getGroups with the full WAG principal name (as Trino would)
            Set<String> groups = groupProvider.getGroups(principalName);
            assertThat(groups).containsExactlyInAnyOrder("GROUP_A", "GROUP_B");

            // Verify the servlet received the base64-encoded transformed username,
            // not the full BPI string
            assertThat(receivedBase64Username.get()).isEqualTo(encodeBase64(principalName));
        }
    }

    @Test
    public void testGetGroupsPreservesUrlEncodedLdapPrincipalName()
            throws Exception
    {
        // LDAP principal with special characters that BPISerializer.sanitize() will URL-encode:
        //   @ -> %40, / -> %2F, : -> %3A, space -> %20
        DatalakeLDAPBloombergPrincipal principal = new DatalakeLDAPBloombergPrincipal(
                "user@dom/a:i n",
                Optional.of(456L));

        // Verify the serialized name contains percent-encoded special characters
        String principalName = principal.getName();
        assertThat(principalName).isEqualTo("bpi:bb_username:user%40dom%2Fa%3Ai%20n:bb_uuid:456");

        // Capture the raw base64-encoded username that the servlet receives
        AtomicReference<String> receivedBase64Username = new AtomicReference<>();

        try (GroupProviderTestServer testServer = new GroupProviderTestServer(
                capturingServlet(receivedBase64Username, "{\"groupList\": [\"LDAP_GROUP\"]}"))) {
            GroupProvider groupProvider = testServer.createGroupProvider();

            Set<String> groups = groupProvider.getGroups(principalName);
            assertThat(groups).containsExactly("LDAP_GROUP");

            // The servlet should receive the base64-encoded full BPI string with
            // percent-encoded characters intact — no WAG UUID extraction, no
            // double-encoding, no decoding
            assertThat(receivedBase64Username.get()).isEqualTo(encodeBase64(principalName));
        }
    }

    @Test
    public void testGetGroupsCachesResultForSameWagPrincipal()
            throws Exception
    {
        DatalakeWAGPrincipal principal = DatalakeWAGPrincipal.builder()
                .primaryIdType("wag")
                .primaryId("11111")
                .applicationFirm(9001)
                .uuid(Optional.of(789L))
                .username(Optional.of("cached_user"))
                .sessionId(Optional.empty())
                .build();

        String principalName = principal.getName();

        AtomicInteger requestCount = new AtomicInteger(0);

        try (GroupProviderTestServer testServer = new GroupProviderTestServer(
                countingServlet(requestCount, "{\"groupList\": [\"CACHED_GROUP\"]}"))) {
            GroupProvider groupProvider = testServer.createGroupProvider();

            // First call — should hit the servlet
            Set<String> groups1 = groupProvider.getGroups(principalName);
            assertThat(groups1).containsExactly("CACHED_GROUP");
            assertThat(requestCount.get()).isEqualTo(1);

            // Second call with the same principal — should be served from cache
            Set<String> groups2 = groupProvider.getGroups(principalName);
            assertThat(groups2).containsExactly("CACHED_GROUP");
            assertThat(requestCount.get()).isEqualTo(1);
        }
    }

    /**
     * Creates a servlet that captures the raw base64-encoded username from the request path.
     */
    private static HttpServlet capturingServlet(AtomicReference<String> receivedBase64Username, String jsonResponse)
    {
        return new HttpServlet()
        {
            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response)
                    throws IOException
            {
                // pathInfo is "/<base64-encoded-username>"
                receivedBase64Username.set(request.getPathInfo().substring(1));

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
            protected void doGet(HttpServletRequest request, HttpServletResponse response)
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
     * AutoCloseable wrapper around a Jetty server that serves as a group provider
     * HTTP endpoint. Use with try-with-resources to ensure the server is stopped.
     */
    private static class GroupProviderTestServer
            implements AutoCloseable
    {
        private final Server server;
        private final int port;
        private final JettyHttpClient httpClient;

        GroupProviderTestServer(HttpServlet servlet)
                throws Exception
        {
            server = new Server();
            ServerConnector connector = new ServerConnector(server);
            connector.setPort(0);
            server.addConnector(connector);

            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            context.setContextPath("/");
            server.setHandler(context);
            context.addServlet(new ServletHolder(servlet), "/api/v1/groupprovider/getGroups/*");

            server.start();
            port = connector.getLocalPort();
            httpClient = new JettyHttpClient();
        }

        GroupProvider createGroupProvider()
        {
            Map<String, String> config = Map.of(
                    "externalhttpgroups.uri", "http://localhost:" + port + "/api/v1/groupprovider",
                    "externalhttpgroups.cache-ttl", "10m");

            return new ExternalHttpGroupsGroupProviderFactory()
                    .create(config, Optional.of(httpClient));
        }

        @Override
        public void close()
                throws Exception
        {
            httpClient.close();
            server.stop();
        }
    }
}

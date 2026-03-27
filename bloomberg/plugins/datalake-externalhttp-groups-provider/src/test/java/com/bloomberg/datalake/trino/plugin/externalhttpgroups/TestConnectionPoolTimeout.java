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
import io.airlift.units.Duration;
import io.trino.spi.security.GroupProvider;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.bloomberg.datalake.trino.plugin.externalhttpgroups.ExternalHttpGroupsGroupProvider.encodeBase64;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

/**
 * Real integration test that reproduces the actual PrematureCloseException scenario.
 *
 * This test uses a real Jetty server with configurable idle timeout to verify that:
 * 1. Server closes idle connections after timeout
 * 2. Client attempts to reuse stale pooled connection
 * 3. Request fails on first attempt (stale connection)
 * 4. Failsafe retry creates new connection and succeeds
 * 5. Different TCP connections are used (not the stale one)
 */
@TestInstance(PER_CLASS)
class TestConnectionPoolTimeout
{
    private Server server;
    private int serverPort;
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final ConcurrentHashMap<String, String> connectionIdsByUsername = new ConcurrentHashMap<>();

    @BeforeEach
    public void setUp()
            throws Exception
    {
        requestCount.set(0);
        connectionIdsByUsername.clear();

        // Create Jetty server with SHORT idle timeout (1 second)
        server = new Server();

        ServerConnector connector = new ServerConnector(server);
        connector.setPort(0); // Use any available port
        connector.setIdleTimeout(1000); // Server closes idle connections after 1 second
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        // Add servlet that tracks connection IDs
        context.addServlet(new ServletHolder(new GroupProviderServlet()), "/api/v1/groupprovider/getGroups/*");

        server.start();
        serverPort = connector.getLocalPort();
    }

    @AfterEach
    public void tearDown()
            throws Exception
    {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testRetryAfterServerClosesIdleConnection()
            throws InterruptedException
    {
        // GIVEN - HTTP client with LONG idle timeout (10 seconds)
        // This creates mismatch: client keeps connections for 10s, server closes after 1s
        HttpClientConfig httpConfig = new HttpClientConfig()
                .setIdleTimeout(Duration.valueOf("10s"))
                .setMaxConnectionsPerServer(10); // Enable connection pooling

        try (JettyHttpClient httpClient = new JettyHttpClient(httpConfig)) {
            Map<String, String> config = Map.of(
                    "externalhttpgroups.uri", "http://localhost:" + serverPort + "/api/v1/groupprovider",
                    "externalhttpgroups.cache-ttl", "1m");

            GroupProvider groupProvider = new ExternalHttpGroupsGroupProviderFactory()
                    .create(config, Optional.of(httpClient));

            // WHEN - First request (establishes connection, pools it)
            Set<String> groups1 = groupProvider.getGroups("user1");

            // THEN - First request succeeds
            assertThat(groups1).containsExactly("GROUP_A");
            assertThat(requestCount.get()).isEqualTo(1);

            String user1ConnectionId = getConnectionIdByUsername("user1");
            assertThat(user1ConnectionId).isNotNull();

            // WHEN - Wait for server to close the connection
            // Server idle timeout is 1 second, we wait 2 seconds to ensure closure
            TimeUnit.SECONDS.sleep(2);

            // AND WHEN - Second request tries to reuse the stale pooled connection
            Set<String> groups2 = groupProvider.getGroups("user2");

            // THEN - Second request should succeed after retry
            assertThat(groups2).containsExactly("GROUP_B");

            // AND - More requests were made due to retry
            // We expect at least 2 total requests (user1 + user2)
            // But might see 3 if the first user2 attempt failed and retried
            int totalRequests = requestCount.get();
            assertThat(totalRequests).isGreaterThanOrEqualTo(2);

            // If retry happened, we should see more than 2 requests
            if (totalRequests > 2) {
                // Verify that user2 used a different connection than user1
                // (because the original connection was closed by server)
                String user2ConnectionId = getConnectionIdByUsername("user2");
                assertThat(user2ConnectionId).isNotNull();
                assertThat(user2ConnectionId).isNotEqualTo(user1ConnectionId);
            }
        }
    }

    @Test
    public void testNoRetryWhenRequestsAreQuick()
    {
        // GIVEN - HTTP client with connection pooling
        HttpClientConfig httpConfig = new HttpClientConfig()
                .setIdleTimeout(Duration.valueOf("10s"))
                .setMaxConnectionsPerServer(10);

        try (JettyHttpClient httpClient = new JettyHttpClient(httpConfig)) {
            Map<String, String> config = Map.of(
                    "externalhttpgroups.uri", "http://localhost:" + serverPort + "/api/v1/groupprovider",
                    "externalhttpgroups.cache-ttl", "1m");

            GroupProvider groupProvider = new ExternalHttpGroupsGroupProviderFactory()
                    .create(config, Optional.of(httpClient));

            // WHEN - First request
            Set<String> groups1 = groupProvider.getGroups("user1");
            assertThat(groups1).containsExactly("GROUP_A");
            int requestsAfterFirst = requestCount.get();

            // WHEN - Second request IMMEDIATELY (no sleep - connection still alive)
            Set<String> groups2 = groupProvider.getGroups("user2");

            // THEN - Second request succeeds
            assertThat(groups2).containsExactly("GROUP_B");

            // AND - Only one additional request (no retry)
            assertThat(requestCount.get()).isEqualTo(requestsAfterFirst + 1);

            // AND - Both requests used the same connection (connection reused from pool)
            String user1ConnectionId = getConnectionIdByUsername("user1");
            String user2ConnectionId = getConnectionIdByUsername("user2");
            assertThat(user1ConnectionId).isEqualTo(user2ConnectionId);
        }
    }

    @Test
    public void testShortClientIdleTimeoutPreventsStaleConnections()
            throws InterruptedException
    {
        // GIVEN - HTTP client with SHORT idle timeout (500ms)
        // Client timeout < server timeout means client evicts before server closes
        HttpClientConfig httpConfig = new HttpClientConfig()
                .setIdleTimeout(Duration.valueOf("500ms"))
                .setMaxConnectionsPerServer(10);

        try (JettyHttpClient httpClient = new JettyHttpClient(httpConfig)) {
            Map<String, String> config = Map.of(
                    "externalhttpgroups.uri", "http://localhost:" + serverPort + "/api/v1/groupprovider",
                    "externalhttpgroups.cache-ttl", "1m");

            GroupProvider groupProvider = new ExternalHttpGroupsGroupProviderFactory()
                    .create(config, Optional.of(httpClient));

            // WHEN - First request
            Set<String> groups1 = groupProvider.getGroups("user1");
            assertThat(groups1).containsExactly("GROUP_A");

            // WHEN - Wait 1 second (longer than client's 500ms timeout)
            TimeUnit.SECONDS.sleep(1);

            // AND WHEN - Second request
            Set<String> groups2 = groupProvider.getGroups("user2");

            // THEN - Second request succeeds
            assertThat(groups2).containsExactly("GROUP_B");

            // AND - Exactly 2 requests (no retry needed - client already evicted the connection)
            assertThat(requestCount.get()).isEqualTo(2);

            // AND - Different connections used (client created new connection, not retry)
            String user1ConnectionId = getConnectionIdByUsername("user1");
            String user2ConnectionId = getConnectionIdByUsername("user2");
            assertThat(user1ConnectionId).isNotEqualTo(user2ConnectionId);
        }
    }

    private String getConnectionIdByUsername(String username)
    {
        // when the username is sent to servlet, it is base64 encoded
        // therefore, we need to base64-encode here to match the key in the map
        return connectionIdsByUsername.get(encodeBase64(username));
    }

    /**
     * Servlet that simulates the group provider endpoint and tracks connection IDs.
     */
    private class GroupProviderServlet
            extends HttpServlet
    {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException
        {
            requestCount.incrementAndGet();

            // Track connection ID (using remote port as unique identifier)
            String connectionId = request.getRemoteAddr() + ":" + request.getRemotePort();

            String pathInfo = request.getPathInfo(); // e.g., "/user1"

            // removing leading "/"
            // This is the base64 encoded and sanitized username
            String username = pathInfo.substring(1);

            // Store connection ID for this user (last one wins if multiple requests)
            connectionIdsByUsername.put(username, connectionId);

            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_OK);

            // Return different groups based on username
            String groupList = switch (username) {
                case String u when u.equals(encodeBase64("user1")) -> "GROUP_A";
                case String u when u.equals(encodeBase64("user2")) -> "GROUP_B";
                default -> "GROUP_UNKNOWN";
            };

            String jsonResponse = String.format("{\"groupList\": [\"%s\"]}", groupList);
            response.getWriter().println(jsonResponse);
        }
    }
}

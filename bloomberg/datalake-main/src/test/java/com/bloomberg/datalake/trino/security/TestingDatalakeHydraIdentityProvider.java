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
package com.bloomberg.datalake.trino.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import io.airlift.http.server.HttpServerConfig;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.node.NodeInfo;
import io.trino.plugin.base.util.AutoCloseableCloser;
import io.trino.testing.containers.PrintingLogConsumer;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.UriBuilder;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class TestingDatalakeHydraIdentityProvider
        implements AutoCloseable
{
    private static final int AUTH_PORT = 4444;
    private static final int ADMIN_PORT = 4445;
    private static final String HYDRA_IMAGE = "artprod.dev.bloomberg.com/datalake/oryd/hydra:v2.2.0";

    private final Network network = Network.SHARED;

    private final AutoCloseableCloser closer = AutoCloseableCloser.create();
    private final ObjectMapper mapper = new ObjectMapper();
    private GenericContainer<?> hydraContainer;

    private String loginSubject;
    private Map<String, Object> extraClaims = ImmutableMap.of();

    public TestingDatalakeHydraIdentityProvider()
    {
        closer.register(network);
    }

    public void start()
            throws Exception
    {
        // Database
        PostgreSQLContainer<?> databaseContainer = new PostgreSQLContainer<>(
                DockerImageName.parse("artprod.dev.bloomberg.com/external-base/images/docker.io/postgres:13").asCompatibleSubstituteFor("postgres"))
                .withLogConsumer(new PrintingLogConsumer("hydra-postgresql"))
                .withNetwork(network)
                .withNetworkAliases("hydra-database")
                .withUsername("hydra")
                .withPassword(UUID.randomUUID().toString())
                .withDatabaseName("hydra");
        closer.register(databaseContainer);
        databaseContainer.start();
        String databaseConnection = format("postgres://%s:%s@%s:5432/%s?sslmode=disable", databaseContainer.getUsername(), databaseContainer.getPassword(),
                databaseContainer.getNetworkAliases().getFirst(), databaseContainer.getDatabaseName());
        // Database migration
        GenericContainer<?> migrationContainer = new GenericContainer<>(HYDRA_IMAGE)
                .withLogConsumer(new PrintingLogConsumer("hydra-migration"))
                .withNetwork(network)
                .withCommand("migrate", "sql", "--yes", databaseConnection)
                .withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(5)));
        closer.register(migrationContainer);
        migrationContainer.start();
        // User authentication server, used by Hydra
        TestingHttpServer userAuthServer = createUserAuthServer();
        closer.register(userAuthServer::stop);
        // Hydra
        hydraContainer = new FixedHostPortGenericContainer<>(HYDRA_IMAGE)
                .withLogConsumer(new PrintingLogConsumer("hydra-server"))
                .withNetwork(network)
                .withNetworkAliases("hydra")
                .withEnv("DEV", "true")
                .withEnv("DSN", databaseConnection)
                .withEnv("URLS_SELF_ISSUER", UriBuilder.newInstance().scheme("http").host("localhost").port(AUTH_PORT).build().toString())
                .withEnv("URLS_CONSENT", UriBuilder.fromUri(userAuthServer.getBaseUrl()).path("/consent").build().toString())
                .withEnv("URLS_LOGIN", UriBuilder.fromUri(userAuthServer.getBaseUrl()).path("/login").build().toString())
                // For the token hook Hydra needs to call a service running on the test host
                .withEnv("OAUTH2_TOKEN_HOOK_URL",
                        UriBuilder.newInstance().scheme("http").host("token-server.local.gate0.net").port(userAuthServer.getPort()).path("/token").build().toString())
                .withEnv("LOG_LEVEL", "debug")
                .withEnv("SECRETS_SYSTEM", UUID.randomUUID().toString())
                .withEnv("SECRETS_COOKIE", UUID.randomUUID().toString())
                .withEnv("SERVE_ADMIN_DEBUG", "true")
                .withEnv("TTL_ACCESS_TOKEN", "60s")
                .withEnv("TTL_ID_TOKEN", "60s")
                .withEnv("LOG_LEAK_SENSITIVE_VALUES", "true")
                .withCommand("serve", "all")
                .withFixedExposedPort(AUTH_PORT, AUTH_PORT)
                .withFixedExposedPort(ADMIN_PORT, ADMIN_PORT)
                .withExtraHost("token-server.local.gate0.net", "host-gateway")
                .waitingFor(new WaitAllStrategy()
                        .withStrategy(Wait.forLogMessage(".*Setting up http server on :4444.*", 1))
                        .withStrategy(Wait.forLogMessage(".*Setting up http server on :4445.*", 1)));
        hydraContainer.start();
        closer.register(hydraContainer);
    }

    public ClientIdSecret createClient(String callbackUrl)
    {
        try (GenericContainer<?> container = new GenericContainer<>(HYDRA_IMAGE)) {
            container.withNetwork(network)
                    .withLogConsumer(new PrintingLogConsumer("hydra-create-client"))
                    .withCommand("create", "oauth2-client",
                            "--format", "json",
                            "--endpoint", "http://hydra:" + ADMIN_PORT,
                            "--skip-tls-verify",
                            "--grant-type", "authorization_code,refresh_token,client_credentials",
                            "--response-type", "token,code,id_token",
                            "--scope", "openid,offline",
                            "--token-endpoint-auth-method", "client_secret_basic",
                            "--redirect-uri", callbackUrl)
                    .withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(Duration.ofSeconds(30)));
            container.start();
            JsonNode result;
            try {
                result = mapper.readTree(container.getLogs());
            }
            catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to parse hydra create oauth2-client output", e);
            }
            String clientId = result.get("client_id").asText();
            String clientSecret = result.get("client_secret").asText();
            return new ClientIdSecret(clientId, clientSecret);
        }
    }

    public URI getAuthBaseUri()
    {
        return UriBuilder.newInstance().scheme("http").host(hydraContainer.getHost()).port(AUTH_PORT).build();
    }

    public URI getAdminBaseUri()
    {
        return UriBuilder.newInstance().scheme("http").host(hydraContainer.getHost()).port(ADMIN_PORT).build();
    }

    public void setLoginSubject(String loginSubject)
    {
        this.loginSubject = requireNonNull(loginSubject, "loginSubject is null");
    }

    public void setExtraClaims(Map<String, Object> extraClaims)
    {
        this.extraClaims = ImmutableMap.copyOf(extraClaims);
    }

    public void reset()
    {
        loginSubject = null;
        extraClaims = ImmutableMap.of();
    }

    @Override
    public void close()
            throws Exception
    {
        closer.close();
    }

    private TestingHttpServer createUserAuthServer()
            throws Exception
    {
        NodeInfo nodeInfo = new NodeInfo("test");
        HttpServerConfig config = new HttpServerConfig().setHttpPort(0);
        HttpServerInfo httpServerInfo = new HttpServerInfo(config, nodeInfo);
        TestingHttpServer testingHttpServer = new TestingHttpServer("test", httpServerInfo, nodeInfo, config, new UserAuthServlet());
        testingHttpServer.start();
        return testingHttpServer;
    }

    private class UserAuthServlet
            extends HttpServlet
    {
        private final OkHttpClient httpClient = new OkHttpClient();

        public UserAuthServlet()
        {
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException
        {
            if (request.getPathInfo().equals("/login")) {
                acceptLogin(request, response);
                return;
            }
            if (request.getPathInfo().contains("/consent")) {
                acceptConsent(request, response);
                return;
            }
            response.setStatus(SC_NOT_FOUND);
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws IOException
        {
            if (request.getPathInfo().contains("/token")) {
                acceptToken(response);
                return;
            }
            response.setStatus(SC_NOT_FOUND);
        }

        private void acceptLogin(HttpServletRequest request, HttpServletResponse response)
                throws IOException
        {
            String loginChallenge = request.getParameter("login_challenge");
            try (Response loginAcceptResponse = acceptLogin(loginChallenge)) {
                sendRedirect(loginAcceptResponse, response);
            }
        }

        private void acceptConsent(HttpServletRequest request, HttpServletResponse response)
                throws IOException
        {
            String consentChallenge = request.getParameter("consent_challenge");
            JsonNode consentRequest = getConsentRequest(consentChallenge);
            try (Response acceptConsentResponse = acceptConsent(consentChallenge, consentRequest)) {
                sendRedirect(acceptConsentResponse, response);
            }
        }

        private void acceptToken(HttpServletResponse response)
                throws IOException
        {
            response.setStatus(SC_OK);
            response.setContentType(APPLICATION_JSON);
            ObjectNode customClaims = mapper.valueToTree(extraClaims);
            response.getWriter().write(mapper.writeValueAsString(
                    mapper.createObjectNode().<ObjectNode>set("session", mapper.createObjectNode()
                            .<ObjectNode>set("access_token", customClaims)
                            .<ObjectNode>set("id_token", customClaims))));
        }

        private Response acceptLogin(String loginChallenge)
                throws IOException
        {
            return httpClient.newCall(
                            new Request.Builder()
                                    .url(HttpUrl.get(getAdminBaseUri()).newBuilder().addPathSegments("/admin/oauth2/auth/requests/login/accept").addQueryParameter(
                                            "login_challenge", loginChallenge).build())
                                    .put(RequestBody.create(
                                            mapper.writeValueAsString(mapper.createObjectNode().put("subject", requireNonNull(loginSubject, "loginSubject is null"))),
                                            MediaType.get(APPLICATION_JSON)))
                                    .build())
                    .execute();
        }

        private JsonNode getConsentRequest(String consentChallenge)
                throws IOException
        {
            try (Response response = httpClient.newCall(
                            new Request.Builder()
                                    .url(HttpUrl.get(getAdminBaseUri()).newBuilder().addPathSegments("/admin/oauth2/auth/requests/consent")
                                            .addQueryParameter("consent_challenge", consentChallenge).build())
                                    .get()
                                    .build())
                    .execute()) {
                return mapper.readTree(response.body().byteStream());
            }
        }

        private Response acceptConsent(String consentChallenge, JsonNode consentRequest)
                throws IOException
        {
            return httpClient.newCall(
                            new Request.Builder()
                                    .url(HttpUrl.get(getAdminBaseUri()).newBuilder().addPathSegments("/admin/oauth2/auth/requests/consent/accept").addQueryParameter(
                                            "consent_challenge", consentChallenge).build())
                                    .put(RequestBody.create(
                                            mapper.writeValueAsString(mapper.createObjectNode()
                                                    .<ObjectNode>set("grant_scope", consentRequest.get("requested_scope"))
                                                    .<ObjectNode>set("grant_access_token_audience", consentRequest.get("requested_access_token_audience"))),
                                            MediaType.get(APPLICATION_JSON)))
                                    .build())
                    .execute();
        }

        private void sendRedirect(Response redirectResponse, HttpServletResponse response)
                throws IOException
        {
            URI containerRedirectUri = UriBuilder.fromUri(mapper.readTree(redirectResponse.body().byteStream()).get("redirect_to").textValue())
                    .host(getAuthBaseUri().getHost())
                    .port(getAuthBaseUri().getPort())
                    .build();
            response.sendRedirect(containerRedirectUri.toString());
        }
    }

    public record ClientIdSecret(String clientId, String clientSecret)
    {
        public ClientIdSecret
        {
            requireNonNull(clientId, "clientId is null");
            requireNonNull(clientSecret, "clientSecret is null");
        }
    }
}

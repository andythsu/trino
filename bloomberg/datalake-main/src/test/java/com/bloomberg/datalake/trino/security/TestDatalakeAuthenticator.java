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

import com.bloomberg.datalake.bpi.DatalakeLDAPBloombergPrincipal;
import com.bloomberg.datalake.trino.server.DatalakeModule;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.HostAndPort;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import io.trino.Session;
import io.trino.SessionRepresentation;
import io.trino.client.auth.external.ExternalAuthenticator;
import io.trino.client.auth.external.HttpTokenPoller;
import io.trino.client.auth.external.KnownToken;
import io.trino.client.auth.external.RedirectException;
import io.trino.client.auth.external.RedirectHandler;
import io.trino.client.auth.external.TokenPoller;
import io.trino.dispatcher.DispatchManager;
import io.trino.execution.QueryInfo;
import io.trino.server.security.PasswordAuthenticatorManager;
import io.trino.server.security.oauth2.OAuth2Client;
import io.trino.server.testing.TestingTrinoServer;
import io.trino.spi.Plugin;
import io.trino.spi.QueryId;
import io.trino.spi.security.Identity;
import io.trino.spi.security.PasswordAuthenticator;
import io.trino.spi.security.PasswordAuthenticatorFactory;
import io.trino.testing.MaterializedResult;
import io.trino.testing.ResultWithQueryId;
import io.trino.testing.TestingGroupProvider;
import io.trino.testing.TestingSession;
import io.trino.testing.TestingTrinoClient;
import jakarta.ws.rs.core.UriBuilder;
import okhttp3.HttpUrl;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;

import java.io.File;
import java.io.IOException;
import java.net.CookieManager;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.io.Resources.getResource;
import static io.airlift.testing.Closeables.closeAll;
import static io.trino.client.OkHttpUtil.basicAuth;
import static io.trino.client.OkHttpUtil.setupInsecureSsl;
import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@TestInstance(PER_CLASS)
@Execution(SAME_THREAD)
public class TestDatalakeAuthenticator
{
    private static final String TEST_USER = "test_user";
    private static final long TEST_UUID = 42;
    private static final String TEST_BPI = format("bpi:bb_username:%s:bb_uuid:%d", TEST_USER, TEST_UUID);
    private static final String PASSWORD = "password";
    private static final Session SESSION = TestingSession.testSessionBuilder()
            .setIdentity(Identity.forUser(TEST_USER).build())
            .setOriginalIdentity(Identity.forUser(TEST_USER).build())
            .build();

    private TestingDatalakeHydraIdentityProvider hydra;
    private TestingTrinoServer server;
    private DispatchManager dispatchManager;
    private OkHttpClient httpClient;
    private CookieManager cookieManager;
    private final TestingGroupProvider testingGroupProvider = new TestingGroupProvider();

    @BeforeAll
    public void setup()
            throws Exception
    {
        int trinoHttpsPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            trinoHttpsPort = socket.getLocalPort();
        }

        hydra = new TestingDatalakeHydraIdentityProvider();
        hydra.start();

        TestingDatalakeHydraIdentityProvider.ClientIdSecret oauthClientCredentials = hydra.createClient(
                UriBuilder.newInstance().scheme("https").host("127.0.0.1").port(trinoHttpsPort).path("/oauth2/callback").build().toString());

        server = TestingTrinoServer.builder()
                .setAdditionalModule(new DatalakeModule())
                .setProperties(ImmutableMap.<String, String>builder()
                        .put("http-server.https.port", Integer.toString(trinoHttpsPort))
                        .put("http-server.https.enabled", "true")
                        .put("http-server.https.keystore.path", getResource("certificate/localhost.pem").getPath())
                        .put("http-server.https.keystore.key", "")
                        .put("http-server.authentication.type", "bloomberg-password,bloomberg-bsso")
                        .put("password-authenticator.config-files", new File(getResource("password-authenticators.properties").toURI()).getPath())
                        .put("http-server.authentication.oauth2.oidc.discovery", "false")
                        .put("http-server.authentication.oauth2.principal-field", "username")
                        .put("http-server.authentication.oauth2.issuer", UriBuilder.fromUri(hydra.getAuthBaseUri()).host("localhost").toString())
                        .put("http-server.authentication.oauth2.auth-url", UriBuilder.fromUri(hydra.getAuthBaseUri()).path("/oauth2/auth").build().toString())
                        .put("http-server.authentication.oauth2.token-url", UriBuilder.fromUri(hydra.getAuthBaseUri()).path("/oauth2/token").build().toString())
                        .put("http-server.authentication.oauth2.end-session-url", UriBuilder.fromUri(hydra.getAuthBaseUri()).path("/oauth2/sessions/logout").toString())
                        .put("http-server.authentication.oauth2.jwks-url", UriBuilder.fromUri(hydra.getAuthBaseUri()).path("/.well-known/jwks.json").toString())
                        .put("http-server.authentication.oauth2.userinfo-url", UriBuilder.fromUri(hydra.getAuthBaseUri()).path("/userinfo").toString())
                        .put("http-server.authentication.oauth2.client-id", oauthClientCredentials.clientId())
                        .put("http-server.authentication.oauth2.client-secret", oauthClientCredentials.clientSecret())
                        .put("http-server.authentication.oauth2.additional-audiences", "*")
                        .put("http-server.authentication.oauth2.max-clock-skew", "0s")
                        .put("web-ui.enabled", "false")
                        .buildOrThrow())
                .build();
        server.installPlugin(new Plugin()
        {
            @Override
            public Iterable<PasswordAuthenticatorFactory> getPasswordAuthenticatorFactories()
            {
                return ImmutableList.of(new PasswordAuthenticatorFactory()
                {
                    @Override
                    public String getName()
                    {
                        return "testing";
                    }

                    @Override
                    public PasswordAuthenticator create(Map<String, String> config)
                    {
                        return (username, password) -> {
                            checkState(username.equals(TEST_USER), "username is not expected value");
                            checkState(password.equals(PASSWORD), "password is not expected value");
                            return new DatalakeLDAPBloombergPrincipal(TEST_USER, Optional.of(TEST_UUID));
                        };
                    }
                });
            }
        });
        // Load modules
        server.getInstance(Key.get(new TypeLiteral<Optional<PasswordAuthenticatorManager>>() {})).ifPresent(PasswordAuthenticatorManager::loadPasswordAuthenticator);
        server.getInstance(Key.get(OAuth2Client.class)).load();
        server.getGroupProvider().setConfiguredGroupProvider(testingGroupProvider);
        // Get DispatchManager instance, used to pull query info
        dispatchManager = server.getInstance(Key.get(DispatchManager.class));

        cookieManager = new CookieManager();
        OkHttpClient.Builder builder = new OkHttpClient.Builder().followRedirects(true).cookieJar(new JavaNetCookieJar(cookieManager));
        setupTrinoHttps(builder);
        setupInsecureSsl(builder);
        httpClient = builder.build();
    }

    @AfterEach
    void reset()
    {
        testingGroupProvider.reset();
        cookieManager.getCookieStore().removeAll();
        hydra.reset();
    }

    @AfterAll
    void destroy()
            throws Exception
    {
        closeAll(server, hydra);
    }

    @Test
    void testPasswordAuthentication()
    {
        OkHttpClient basicAuthClient = httpClient.newBuilder().addInterceptor(basicAuth(TEST_USER, PASSWORD)).build();
        Set<String> bpiGroups = ImmutableSet.of("test-bpi-group");
        testingGroupProvider.setUserGroups(ImmutableMap.of(
                TEST_BPI, bpiGroups,
                TEST_USER, ImmutableSet.of("test-ldap-group")));

        testCurrentUserInfoQuery(basicAuthClient, SESSION, TEST_BPI, TEST_BPI, bpiGroups);
    }

    @Test
    void testPasswordImpersonation()
    {
        OkHttpClient basicAuthClient = httpClient.newBuilder().addInterceptor(basicAuth(TEST_USER, PASSWORD)).build();
        String impersonatedUser = "impersonated-user";
        Set<String> impersonatedUserGroups = ImmutableSet.of("test-impersonation-group");
        testingGroupProvider.setUserGroups(ImmutableMap.of(
                TEST_BPI, ImmutableSet.of("test-bpi-group"),
                TEST_USER, ImmutableSet.of("test-ldap-group"),
                impersonatedUser, impersonatedUserGroups));
        Session session = Session.builder(SESSION).setIdentity(Identity.ofUser(impersonatedUser)).build();

        testCurrentUserInfoQuery(basicAuthClient, session, impersonatedUser, TEST_BPI, impersonatedUserGroups);
    }

    @Test
    void testBSSOAuthentication()
    {
        OkHttpClient.Builder builder = httpClient.newBuilder();
        setupExternalAuthenticator(builder);
        OkHttpClient externalAuthenticationClient = builder.build();

        Set<String> bpiGroups = ImmutableSet.of("test-bpi-oauth-group");
        testingGroupProvider.setUserGroups(ImmutableMap.of(
                TEST_BPI, bpiGroups,
                TEST_USER, ImmutableSet.of("test-regular-group")));

        hydra.setLoginSubject(TEST_USER);
        hydra.setExtraClaims(ImmutableMap.of("username", TEST_USER, "employeeid", TEST_UUID));

        testCurrentUserInfoQuery(externalAuthenticationClient, SESSION, TEST_BPI, TEST_BPI, bpiGroups);
    }

    @Test
    void testBSSOImpersonation()
    {
        OkHttpClient.Builder builder = httpClient.newBuilder();
        setupExternalAuthenticator(builder);
        OkHttpClient externalAuthenticationClient = builder.build();

        String impersonatedUser = "impersonated-oauth-user";
        Set<String> impersonatedUserGroups = ImmutableSet.of("test-oauth-impersonation-group");
        Set<String> bpiGroups = ImmutableSet.of("test-bpi-oauth-group");
        testingGroupProvider.setUserGroups(ImmutableMap.of(
                TEST_BPI, bpiGroups,
                TEST_USER, ImmutableSet.of("test-regular-group"),
                impersonatedUser, impersonatedUserGroups));
        Session session = Session.builder(SESSION).setIdentity(Identity.ofUser(impersonatedUser)).build();

        hydra.setLoginSubject(TEST_USER);
        hydra.setExtraClaims(ImmutableMap.of("username", TEST_USER, "employeeid", TEST_UUID));

        testCurrentUserInfoQuery(externalAuthenticationClient, session, impersonatedUser, TEST_BPI, impersonatedUserGroups);
    }

    private void testCurrentUserInfoQuery(OkHttpClient httpClient, Session session, String expectedUser, String expectedPrincipal, Collection<String> expectedGroups)
    {
        QueryInfoAndResult currentUserQueryInfoAndResult = executeQueryFetchSingleResult("SELECT current_user", httpClient, session);
        assertThat(currentUserQueryInfoAndResult.result()).isEqualTo(expectedUser);
        checkQueryInfoUserAndGroups(currentUserQueryInfoAndResult.queryInfo(), expectedUser, expectedPrincipal, expectedGroups);
        QueryInfoAndResult groupsQueryInfoAndResult = executeQueryFetchSingleResult("SELECT current_groups()", httpClient, session);
        assertThat(Splitter.on(",").splitToList(groupsQueryInfoAndResult.result()).stream().map(group -> group.replaceAll("[\\[\\]]", "")))
                .containsExactlyElementsOf(expectedGroups);
        checkQueryInfoUserAndGroups(groupsQueryInfoAndResult.queryInfo(), expectedUser, expectedPrincipal, expectedGroups);
    }

    private QueryInfoAndResult executeQueryFetchSingleResult(@Language("SQL") String query, OkHttpClient httpClient, Session session)
    {
        try (TestingTrinoClient client = new TestingTrinoClient(server, session, httpClient)) {
            ResultWithQueryId<MaterializedResult> resultWithQueryId = client.execute(query);
            String result = resultWithQueryId.getResult().getMaterializedRows().getFirst().getField(0).toString();
            QueryInfo queryInfo = getQueryInfo(resultWithQueryId.getQueryId());
            return new QueryInfoAndResult(queryInfo, result);
        }
    }

    private QueryInfo getQueryInfo(QueryId queryId)
    {
        return dispatchManager.getFullQueryInfo(queryId).orElseThrow(() -> new IllegalStateException("Query not found"));
    }

    private void checkQueryInfoUserAndGroups(QueryInfo queryInfo, String user, String principal, Collection<String> groups)
    {
        SessionRepresentation session = queryInfo.getSession();
        assertThat(session.getUser()).isEqualTo(user);
        assertThat(session.getOriginalUser()).isEqualTo(user);
        assertThat(session.getGroups()).isEqualTo(groups);
        assertThat(session.getPrincipal()).isPresent().get().isEqualTo(principal);
    }

    private void setupTrinoHttps(OkHttpClient.Builder builder)
    {
        builder.addInterceptor(chain -> {
            Request request = chain.request();
            HttpUrl url = request.url();
            if (url.scheme().equals("http") && HostAndPort.fromParts(url.host(), url.port()).equals(server.getAddress())) {
                return chain.proceed(request.newBuilder()
                        .url(request.url().newBuilder().scheme("https").host(server.getHttpsAddress().getHost()).port(server.getHttpsAddress().getPort()).build())
                        .build());
            }
            return chain.proceed(request);
        });
    }

    private void setupExternalAuthenticator(OkHttpClient.Builder builder)
    {
        RedirectHandler redirectHandler = uri -> {
            Request request = new Request.Builder().url(HttpUrl.get(uri)).build();
            try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                if (response.code() != HTTP_OK) {
                    throw new RedirectException(format("HTTP GET failed with status %d and body %s", response.code(), response.body().string()));
                }
            }
            catch (IOException e) {
                throw new RedirectException("Redirection failed", e);
            }
        };
        TokenPoller poller = new HttpTokenPoller(builder.build());
        ExternalAuthenticator authenticator = new ExternalAuthenticator(
                redirectHandler, poller, KnownToken.local(), Duration.ofMinutes(2));
        builder.authenticator(authenticator);
        builder.addNetworkInterceptor(authenticator);
    }

    record QueryInfoAndResult(QueryInfo queryInfo, String result)
    {
        QueryInfoAndResult
        {
            requireNonNull(queryInfo, "query info is null");
            requireNonNull(result, "result is null");
        }
    }
}

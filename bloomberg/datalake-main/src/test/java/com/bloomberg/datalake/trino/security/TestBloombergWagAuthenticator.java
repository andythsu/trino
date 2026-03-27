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

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.server.HttpServerConfig;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.node.NodeInfo;
import io.airlift.units.Duration;
import io.trino.server.security.AuthenticationException;
import io.trino.spi.security.Identity;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.bloomberg.datalake.trino.security.BloombergWagAuthenticator.WAG_JWT_HEADER;
import static io.airlift.security.pem.PemWriter.writePublicKey;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static jakarta.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

/**
 * Unit and integration tests for {@link BloombergWagAuthenticator}.
 */
@TestInstance(PER_CLASS)
public class TestBloombergWagAuthenticator
{
    private static final String TEST_USERNAME = "testuser";
    private static final long TEST_UUID = 12345L;
    private static final String TEST_SESSION_ID = "test-session-id-12345";
    private static final String TEST_PRIMARY_ID_TYPE = "portplus";
    private static final int TEST_FIRM_NUMBER = 1;
    // Expected BPI-serialized identity from DatalakeWAGPrincipal.getName()
    private static final String EXPECTED_IDENTITY_USER = "bpi:wag:bb_firm:1:bb_uuid:12345:primary_id:testuser:primary_id_type:portplus";

    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;
    private TestingHttpServer publicKeyServer;
    private JettyHttpClient httpClient;
    private URI publicKeyUri;
    private ConfigurablePublicKeyServlet configurableServlet;

    @BeforeAll
    void setUp()
            throws Exception
    {
        // Generate RSA key pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        publicKey = (RSAPublicKey) keyPair.getPublic();
        privateKey = (RSAPrivateKey) keyPair.getPrivate();

        // Create configurable servlet
        configurableServlet = new ConfigurablePublicKeyServlet();

        // Start HTTP server to serve the public key
        NodeInfo nodeInfo = new NodeInfo("test");
        HttpServerConfig config = new HttpServerConfig().setHttpPort(0);
        HttpServerInfo httpServerInfo = new HttpServerInfo(config, nodeInfo);
        publicKeyServer = new TestingHttpServer("test", httpServerInfo, nodeInfo, config, configurableServlet);
        publicKeyServer.start();

        publicKeyUri = URI.create(publicKeyServer.getBaseUrl() + "/public-key");

        // Create HTTP client
        httpClient = new JettyHttpClient(new HttpClientConfig()
                .setConnectTimeout(new Duration(10, TimeUnit.SECONDS)));
    }

    @AfterAll
    void tearDown()
            throws Exception
    {
        if (publicKeyServer != null) {
            publicKeyServer.stop();
        }
        if (httpClient != null) {
            httpClient.close();
        }
    }

    @BeforeEach
    void tetsSetUp()
    {
        configurableServlet.reset();
    }

    private String createValidJwt()
    {
        return createJwt(Instant.now().plus(1, ChronoUnit.HOURS));
    }

    private String createExpiredJwt()
    {
        return createJwt(Instant.now().minus(1, ChronoUnit.HOURS));
    }

    private String createJwt(Instant expiresAt)
    {
        return createJwt(expiresAt, Algorithm.RSA256(publicKey, privateKey));
    }

    private String createJwt(Instant expiresAt, Algorithm algorithm)
    {
        // JWT structure required by ContextJWTParser:
        // - "application" claim (Map): primaryIdType, primaryId, firmNumber
        // - "context" claim (Map): uuid (optional), userName (optional), sessionId (optional)
        return JWT.create()
                .withSubject(TEST_USERNAME)
                .withClaim("application", Map.of(
                        "primaryIdType", TEST_PRIMARY_ID_TYPE,
                        "primaryId", TEST_USERNAME,
                        "firmNumber", TEST_FIRM_NUMBER))
                .withClaim("context", Map.of(
                        "uuid", TEST_UUID,
                        "userName", TEST_USERNAME,
                        "sessionId", TEST_SESSION_ID))
                .withExpiresAt(Date.from(expiresAt))
                .withIssuedAt(Date.from(Instant.now()))
                .sign(algorithm);
    }

    private String createUnsignedJwt()
    {
        return createJwt(Instant.now().plus(1, ChronoUnit.HOURS), Algorithm.none());
    }

    private String createJwtSignedWithDifferentKey()
            throws Exception
    {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair differentKeyPair = keyGen.generateKeyPair();
        RSAPublicKey differentPublicKey = (RSAPublicKey) differentKeyPair.getPublic();
        RSAPrivateKey differentPrivateKey = (RSAPrivateKey) differentKeyPair.getPrivate();

        return createJwt(Instant.now().plus(1, ChronoUnit.HOURS), Algorithm.RSA256(differentPublicKey, differentPrivateKey));
    }

    /**
     * Configurable servlet that can be reset with different behaviors for each test.
     */
    private class ConfigurablePublicKeyServlet
            extends HttpServlet
    {
        private volatile int failuresBeforeSuccess;
        private volatile int errorCode = SC_OK;
        private final AtomicInteger requestCount = new AtomicInteger(0);

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws java.io.IOException
        {
            int count = requestCount.getAndIncrement();
            if (count < failuresBeforeSuccess) {
                response.sendError(errorCode);
                return;
            }
            response.setStatus(SC_OK);
            response.setContentType("application/x-pem-file");
            response.getWriter().write(writePublicKey(publicKey));
        }

        public void reset()
        {
            failuresBeforeSuccess = 0;
            errorCode = SC_OK;
            requestCount.set(0);
        }

        public void configureFailures(int failuresBeforeSuccess, int errorCode)
        {
            this.failuresBeforeSuccess = failuresBeforeSuccess;
            this.errorCode = errorCode;
            this.requestCount.set(0);
        }

        public int getRequestCount()
        {
            return requestCount.get();
        }
    }

    @Test
    void testWagJwtHeaderConstant()
    {
        assertThat(WAG_JWT_HEADER).isEqualTo("X-Context-Jwt");
    }

    @Test
    void testAuthenticateWithValidJwt()
            throws AuthenticationException
    {
        configurableServlet.reset();

        BloombergWagConfig config = new BloombergWagConfig()
                .setWagPublicKeyUri(publicKeyUri)
                .setPublicKeyCacheTTL(new Duration(1, TimeUnit.HOURS));

        BloombergWagAuthenticator authenticator = new BloombergWagAuthenticator(httpClient, config);

        String jwt = createValidJwt();

        Identity identity = authenticator.authenticate(jwt);

        assertValidIdentity(identity);
        assertThat(identity.getExtraCredentials()).containsEntry(WAG_JWT_HEADER, jwt);
    }

    private void assertValidIdentity(Identity identity)
    {
        assertThat(identity).isNotNull();
        assertThat(identity.getUser()).isEqualTo(EXPECTED_IDENTITY_USER);
        assertThat(identity.getPrincipal()).isPresent();

        // Verify user attributes structure from DatalakeWAGPrincipal.asIdentityUserAttributes()
        assertThat(identity.getUserAttributes()).containsKey("IDENTITY");
        @SuppressWarnings("unchecked")
        Map<String, Object> identityAttributes = (Map<String, Object>) identity.getUserAttributes().get("IDENTITY");
        assertThat(identityAttributes).isEqualTo(Map.of(
                "primaryIdType", TEST_PRIMARY_ID_TYPE,
                "primaryId", TEST_USERNAME,
                "firmNumber", TEST_FIRM_NUMBER,
                "username", TEST_USERNAME,
                "uuid", TEST_UUID));
    }

    private void assertAuthenticationFails(String jwt)
    {
        configurableServlet.reset();

        BloombergWagConfig config = new BloombergWagConfig()
                .setWagPublicKeyUri(publicKeyUri)
                .setPublicKeyCacheTTL(new Duration(1, TimeUnit.HOURS));

        BloombergWagAuthenticator authenticator = new BloombergWagAuthenticator(httpClient, config);

        assertThatThrownBy(() -> authenticator.authenticate(jwt))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("Authentication failed");
    }

    private BloombergWagConfig createConfig(URI uri)
    {
        return new BloombergWagConfig()
                .setWagPublicKeyUri(uri)
                .setPublicKeyCacheTTL(Duration.valueOf("1ms"))
                .setPublicKeyFetchMaxRetries(3)
                .setPublicKeyFetchRetryInitialDelay(Duration.valueOf("10ms"))
                .setPublicKeyFetchRetryMaxDelay(Duration.valueOf("100ms"))
                .setPublicKeyFetchTimeout(Duration.valueOf("5s"));
    }

    private BloombergWagAuthenticator createWagAuthenticator(URI publicKeyUri)
    {
        return new BloombergWagAuthenticator(httpClient, createConfig(publicKeyUri));
    }

    @Test
    void testAuthenticateWithInvalidJwt()
    {
        assertAuthenticationFails("invalid-jwt-token");
    }

    @Test
    void testAuthenticateWithEmptyJwtValue()
    {
        assertAuthenticationFails("");
    }

    @Test
    void testAuthenticateWithExpiredJwt()
    {
        assertAuthenticationFails(createExpiredJwt());
    }

    @Test
    void testAuthenticateWithUnsignedJwt()
    {
        assertAuthenticationFails(createUnsignedJwt());
    }

    @Test
    void testAuthenticateWithJwtSignedByDifferentKey()
            throws Exception
    {
        assertAuthenticationFails(createJwtSignedWithDifferentKey());
    }

    @Test
    void testNoRetryOnFirstSuccess()
            throws AuthenticationException
    {
        BloombergWagAuthenticator authenticator = createWagAuthenticator(publicKeyUri);
        Identity identity = authenticator.authenticate(createValidJwt());
        assertValidIdentity(identity);

        assertThat(configurableServlet.getRequestCount()).isEqualTo(1);
    }

    @Test
    void testRetrySucceedsAfterTransient503Error()
            throws AuthenticationException
    {
        // Server fails twice with 503, then succeeds
        configurableServlet.configureFailures(2, SC_SERVICE_UNAVAILABLE);

        BloombergWagAuthenticator authenticator = createWagAuthenticator(publicKeyUri);
        Identity identity = authenticator.authenticate(createValidJwt());
        assertValidIdentity(identity);

        // Verify that retries happened (1 initial + 2 retries = 3 total requests)
        assertThat(configurableServlet.getRequestCount()).isEqualTo(3);
    }

    @Test
    void testFailureAfterExhaustingAllRetries()
    {
        // Server always fails with 503 (using high failure count)
        configurableServlet.configureFailures(100, SC_SERVICE_UNAVAILABLE);

        BloombergWagAuthenticator authenticator = createWagAuthenticator(publicKeyUri);

        assertThatThrownBy(() -> authenticator.authenticate(createValidJwt()))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("Authentication failed");

        // Verify max retries were attempted (1 initial + 3 retries = 4 total requests)
        assertThat(configurableServlet.getRequestCount()).isEqualTo(4);
    }

    void testThrowsOn404()
    {
        URI notFoundUri = publicKeyUri.resolve("/not-found");
        BloombergWagAuthenticator authenticator = createWagAuthenticator(notFoundUri);

        assertThatThrownBy(() -> authenticator.authenticate(createValidJwt()))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("Authentication failed");

        assertThat(configurableServlet.getRequestCount()).isEqualTo(0);
    }

    private void testNoRetryOnErrorCode(int errorCode)
    {
        configurableServlet.configureFailures(100, errorCode);
        BloombergWagAuthenticator authenticator = createWagAuthenticator(publicKeyUri);

        assertThatThrownBy(() -> authenticator.authenticate(createValidJwt()))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("Authentication failed");

        assertThat(configurableServlet.getRequestCount()).isEqualTo(1);
    }

    @Test
    void testDoesNotRetryNonRetryableErrors()
    {
        testNoRetryOnErrorCode(400); // client error
        testNoRetryOnErrorCode(403); // forbidden
    }

    @Test
    void testConstructorFailsWhenPublicKeyUriNotSet()
    {
        BloombergWagConfig config = new BloombergWagConfig();
        // wagPublicKeyUri is not set
        assertThatThrownBy(() -> new BloombergWagAuthenticator(httpClient, config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("wagPublicKeyUri is required");
    }
}

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

import com.bloomberg.datalake.bpi.DatalakeBSSOBloombergPrincipal;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.inject.Inject;
import com.google.inject.Module;
import io.airlift.http.client.HttpClient;
import io.airlift.log.Logger;
import io.trino.server.security.AbstractBearerAuthenticator;
import io.trino.server.security.AuthenticationException;
import io.trino.server.security.SecurityConfig;
import io.trino.server.security.oauth2.OAuth2AuthenticationSupportModule;
import io.trino.server.security.oauth2.OAuth2Client;
import io.trino.server.security.oauth2.OAuth2Config;
import io.trino.server.security.oauth2.TokenPairSerializer;
import io.trino.server.security.oauth2.TokenRefresher;
import io.trino.spi.security.Identity;
import jakarta.ws.rs.container.ContainerRequestContext;

import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.trino.server.security.ServerSecurityModule.authenticatorModule;
import static io.trino.server.security.oauth2.OAuth2TokenExchangeResource.getInitiateUri;
import static io.trino.server.security.oauth2.OAuth2TokenExchangeResource.getTokenUri;
import static io.trino.server.security.oauth2.TokenPairSerializer.TokenPair;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class DatalakeOAuth2Authenticator
        extends AbstractBearerAuthenticator
{
    private static final String WAG_JWT_HEADER = "X-Context-Jwt";
    private static final String WAG_JWT_PREFIX = WAG_JWT_HEADER + "=";
    private static final Splitter CREDENTIAL_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();
    private static final String UUID_CLAIM = "employeeid";
    private static final String SESSION_ID_CLAIM = "sessionId";
    private static final Logger log = Logger.get(DatalakeOAuth2Authenticator.class);

    private final OAuth2Client client;
    private final String principalField;
    private final TokenPairSerializer tokenPairSerializer;
    private final TokenRefresher tokenRefresher;
    private final UserHeaderRewriter userHeaderRewriter;
    private final Optional<URI> wagPublicKeyUri;
    private final BloombergWagConfig wagConfig;
    private final HttpClient wagHttpClient;

    @Inject
    public DatalakeOAuth2Authenticator(
            OAuth2Client client,
            OAuth2Config config,
            TokenRefresher tokenRefresher,
            TokenPairSerializer tokenPairSerializer,
            UserHeaderRewriter userHeaderRewriter,
            BloombergWagConfig wagConfig,
            @ForWag HttpClient wagHttpClient)
    {
        this.client = requireNonNull(client, "service is null");
        this.principalField = config.getPrincipalField();
        this.tokenRefresher = requireNonNull(tokenRefresher, "tokenRefresher is null");
        this.tokenPairSerializer = requireNonNull(tokenPairSerializer, "tokenPairSerializer is null");
        this.userHeaderRewriter = requireNonNull(userHeaderRewriter, "userHeaderRewriter is null");
        this.wagConfig = wagConfig;
        this.wagPublicKeyUri = wagConfig.getWagPublicKeyUri();
        this.wagHttpClient = requireNonNull(wagHttpClient, "wagHttpClient is null");
    }

    public static Module module(SecurityConfig securityConfig)
    {
        return authenticatorModule(securityConfig, "bloomberg-bsso", DatalakeOAuth2Authenticator.class, new OAuth2AuthenticationSupportModule());
    }

    @VisibleForTesting
    static String getStringClaim(Map<String, Object> claims, String claimName)
    {
        Object claim = claims.get(claimName);
        if (claim == null) {
            throw new IllegalArgumentException(format("Claim %s is null or missing", claimName));
        }
        if (!(claim instanceof String claimString)) {
            throw new IllegalArgumentException(format("Claim %s is not a string", claimName));
        }
        // Empty strings are rejected because security-related claims (username, sessionId, primaryId)
        // should always have meaningful values. An empty value indicates malformed token data.
        if (claimString.trim().isEmpty()) {
            throw new IllegalArgumentException(format("Claim %s is an empty string", claimName));
        }
        return claimString;
    }

    @VisibleForTesting
    static Optional<String> getOptionalStringClaim(Map<String, Object> claims, String claimName)
    {
        if (!claims.containsKey(claimName)) {
            return Optional.empty();
        }
        return Optional.of(getStringClaim(claims, claimName));
    }

    @VisibleForTesting
    static Long getNumberClaim(Map<String, Object> claims, String claimName)
    {
        Object claim = claims.get(claimName);
        return switch (claim) {
            case null -> throw new IllegalArgumentException(format("Claim %s is null or missing", claimName));
            case Number number -> number.longValue();
            case String claimString -> {
                try {
                    yield Long.parseLong(claimString);
                }
                catch (NumberFormatException e) {
                    throw new IllegalArgumentException(format("Claim %s is not a valid number: %s", claimName, claimString));
                }
            }
            default -> throw new IllegalArgumentException(format("Claim %s has unexpected type: %s", claimName, claim.getClass().getSimpleName()));
        };
    }

    @VisibleForTesting
    static Optional<Long> getOptionalNumberClaim(Map<String, Object> claims, String claimName)
    {
        if (!claims.containsKey(claimName)) {
            return Optional.empty();
        }
        return Optional.of(getNumberClaim(claims, claimName));
    }

    @Override
    public Identity authenticate(ContainerRequestContext request)
            throws AuthenticationException
    {
        List<String> extraCredentials = request.getHeaders().get("X-Trino-Extra-Credential");
        Optional<String> wagJwt = wagPublicKeyUri.flatMap(_ -> findWagJwt(extraCredentials));

        Identity identity = wagJwt.isPresent()
                ? new BloombergWagAuthenticator(wagHttpClient, wagConfig).authenticate(wagJwt.get())
                : super.authenticate(request);

        userHeaderRewriter.rewriteUserHeaders(identity, request.getHeaders());
        return identity;
    }

    // Parsing logic based on HttpRequestSessionContextFactory.splitHttpHeader and parseProperty
    @VisibleForTesting
    static Optional<String> findWagJwt(List<String> extraCredentials)
    {
        if (extraCredentials == null) {
            return Optional.empty();
        }
        return extraCredentials.stream()
                .flatMap(CREDENTIAL_SPLITTER::splitToStream)
                .filter(pair -> pair.startsWith(WAG_JWT_PREFIX))
                .map(pair -> pair.substring(WAG_JWT_PREFIX.length()))
                .findFirst();
    }

    @Override
    protected Optional<Identity> createIdentity(String token)
    {
        return deserializeToken(token)
                .filter(tokenPair -> !tokenPair.expiration().before(Date.from(Instant.now())))
                .flatMap(tokenPair -> client.getAccessTokenClaims(tokenPair.accessToken()))
                .map(claims -> new DatalakeBSSOBloombergPrincipal(getStringClaim(claims, principalField), getOptionalNumberClaim(claims, UUID_CLAIM), getOptionalStringClaim(claims, SESSION_ID_CLAIM)))
                .map(principal -> Identity.forUser(principal.getName()).withPrincipal(principal).build());
    }

    @Override
    public AuthenticationException needAuthentication(ContainerRequestContext request, Optional<String> currentToken, String message)
    {
        return currentToken
                .flatMap(this::deserializeToken)
                .flatMap(tokenRefresher::refreshToken)
                .map(refreshId -> request.getUriInfo().getBaseUri().resolve(getTokenUri(refreshId)))
                .map(tokenUri -> new AuthenticationException(message, format("Bearer x_token_server=\"%s\"", tokenUri)))
                .orElseGet(() -> needAuthentication(request, message));
    }

    private Optional<TokenPair> deserializeToken(String token)
    {
        try {
            return Optional.of(tokenPairSerializer.deserialize(token));
        }
        catch (RuntimeException ex) {
            log.debug(ex, "Failed to deserialize token");
            return Optional.empty();
        }
    }

    private AuthenticationException needAuthentication(ContainerRequestContext request, String message)
    {
        UUID authId = UUID.randomUUID();
        URI initiateUri = request.getUriInfo().getBaseUri().resolve(getInitiateUri(authId));
        URI tokenUri = request.getUriInfo().getBaseUri().resolve(getTokenUri(authId));
        return new AuthenticationException(message, format("Bearer x_redirect_server=\"%s\", x_token_server=\"%s\"", initiateUri, tokenUri));
    }
}

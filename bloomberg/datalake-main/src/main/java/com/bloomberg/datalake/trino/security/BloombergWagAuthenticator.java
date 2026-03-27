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

import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.bloomberg.datalake.bpi.CachingRSAKeyProvider;
import com.bloomberg.datalake.bpi.ContextJWTParser;
import com.bloomberg.datalake.bpi.DatalakeWAGPrincipal;
import com.google.common.collect.ImmutableMap;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import dev.failsafe.Timeout;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.Request;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.log.Logger;
import io.airlift.security.pem.PemReader;
import io.trino.server.security.AuthenticationException;
import io.trino.spi.security.Identity;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static io.airlift.http.client.HttpStatus.familyForStatusCode;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static java.util.Objects.requireNonNull;

public class BloombergWagAuthenticator
{
    public static final String WAG_JWT_HEADER = "X-Context-Jwt";

    private static final Logger log = Logger.get(BloombergWagAuthenticator.class);

    private final HttpClient httpClient;
    private final ContextJWTParser contextJWTParser;
    private final URI publicKeyURI;
    private final RetryPolicy<StringResponse> retry;
    private final Timeout<StringResponse> timeout;

    public BloombergWagAuthenticator(HttpClient httpClient, BloombergWagConfig config)
    {
        this.httpClient = requireNonNull(httpClient, "httpClient is null");
        requireNonNull(config, "config is null");

        this.publicKeyURI = config.getWagPublicKeyUri()
                .orElseThrow(() -> new IllegalArgumentException("wagPublicKeyUri is required"));
        this.retry = RetryPolicy.<StringResponse>builder()
                .handleResultIf(r -> familyForStatusCode(r.getStatusCode()) == HttpStatus.Family.SERVER_ERROR)
                .handle(RuntimeException.class)
                .withBackoff(config.getPublicKeyFetchRetryInitialDelay().toJavaTime(),
                        config.getPublicKeyFetchRetryMaxDelay().toJavaTime())
                .withJitter(0.25)
                .withMaxRetries(config.getPublicKeyFetchMaxRetries())
                .build();
        this.timeout = Timeout.of(config.getPublicKeyFetchTimeout().toJavaTime());

        RSAKeyProvider rsaKeyProvider = new CachingRSAKeyProvider(new RSAKeyProvider()
        {
            @Override
            public RSAPublicKey getPublicKeyById(String s)
            {
                return getWagPublicKey();
            }

            @Override
            public RSAPrivateKey getPrivateKey()
            {
                throw new UnsupportedOperationException("Cannot get private key");
            }

            @Override
            public String getPrivateKeyId()
            {
                throw new UnsupportedOperationException("Cannot get private key");
            }
        }, Optional.of(config.getPublicKeyCacheTTL().toJavaTime()));
        this.contextJWTParser = new ContextJWTParser(rsaKeyProvider);
    }

    private RSAPublicKey getWagPublicKey()
    {
        AtomicInteger attemptCount = new AtomicInteger(0);
        try {
            StringResponse response = Failsafe.with(retry, timeout).get(() -> {
                attemptCount.incrementAndGet();
                Request request = prepareGet().setUri(publicKeyURI).build();
                return httpClient.execute(request, createStringResponseHandler());
            });

            if (familyForStatusCode(response.getStatusCode()) != HttpStatus.Family.SUCCESSFUL) {
                throw new RuntimeException("unexpected status code %d".formatted(response.getStatusCode()));
            }
            return (RSAPublicKey) PemReader.loadPublicKey(response.getBody());
        }
        catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to parse public key", e);
        }
        catch (RuntimeException e) {
            throw new RuntimeException("Failed to fetch public key after " + attemptCount.get() + " attempts: " + e.getMessage(), e);
        }
    }

    public Identity authenticate(String wagJwt)
            throws AuthenticationException
    {
        try {
            DatalakeWAGPrincipal principal = contextJWTParser.parseJwtToWagPrincipal(wagJwt);

            return Identity.forUser(principal.getName())
                    .withPrincipal(principal)
                    .withAdditionalUserAttributes(ImmutableMap.of("IDENTITY", principal.asIdentityUserAttributes()))
                    .withExtraCredentials(ImmutableMap.of(WAG_JWT_HEADER, wagJwt))
                    .build();
        }
        catch (Exception e) {
            log.error("Failed to build identity from token: %s", e.getMessage());
            throw new AuthenticationException("Authentication failed");
        }
    }
}

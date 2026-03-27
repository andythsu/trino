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

import com.bloomberg.codegen.sesget.SessionResponse;
import com.bloomberg.datalake.bpi.DatalakeBSSOBloombergPrincipal;
import com.bloomberg.datalake.bpi.DatalakeLDAPBloombergPrincipal;
import com.bloomberg.datalake.bpi.DatalakePrincipal;
import com.bloomberg.datalake.bpi.DatalakeWAGPrincipal;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;
import io.trino.spi.classloader.ThreadContextClassLoader;
import io.trino.spi.security.UserAttributeProvider;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.json.JsonCodec.jsonCodec;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.Objects.requireNonNull;

public class DatalakeUserAttributeProvider
        implements UserAttributeProvider
{
    private static final Logger log = Logger.get(DatalakeUserAttributeProvider.class);

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration RETRY_BACKOFF_INITIAL_DELAY = Duration.ofMillis(100);
    private static final Duration RETRY_BACKOFF_MAX_DELAY = Duration.ofSeconds(3);
    private static final JsonCodec<UserAttributeProviderRequest> REQUEST_CODEC = jsonCodec(UserAttributeProviderRequest.class);
    private static final JsonCodec<UserAttributeProviderResponse> RESPONSE_CODEC = jsonCodec(UserAttributeProviderResponse.class);

    private final SesgetBasClient sesgetBasClient;
    private final boolean sesBasEnabled;
    private final Optional<String> blpSessionAttributesNode;
    private final JsonCodec<Map<String, Object>> jsonCodec;
    private final LoadingCache<String, ExpiringUserAttributes> userAttributesCache;
    private final HttpClient httpClient;
    private final URI fetchUserAttributesUri;
    private final Cache<HttpCacheKey, Map<String, Object>> httpAttributeCache;
    private final RetryPolicy<Object> retryPolicy;

    @Inject
    public DatalakeUserAttributeProvider(
            SesgetBasClient sesgetBasClient,
            JsonCodec<Map<String, Object>> jsonCodec,
            DatalakeUserAttributeProviderConfig config,
            @ForHttpUserAttributeProvider HttpClient httpClient,
            Ticker ticker)
    {
        this.sesgetBasClient = requireNonNull(sesgetBasClient, "sesgetBasClient is null");
        this.sesBasEnabled = config.isSesBasEnabled();
        this.blpSessionAttributesNode = config.getBlpSessionAttributesNode();
        this.jsonCodec = requireNonNull(jsonCodec, "jsonCodec is null");
        this.httpClient = requireNonNull(httpClient, "httpClient is null");
        this.userAttributesCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfter(new Expiry<String, ExpiringUserAttributes>()
                {
                    @Override
                    public long expireAfterCreate(String string, ExpiringUserAttributes expiringUserAttributes, long currentTime)
                    {
                        long nanos = Duration.between(OffsetDateTime.now(), expiringUserAttributes.expireDateTime()).toNanos();
                        return Math.max(nanos, 0);
                    }

                    @Override
                    public long expireAfterUpdate(String string, ExpiringUserAttributes expiringUserAttributes, long currentTime, long currentDuration)
                    {
                        return currentDuration;
                    }

                    @Override
                    public long expireAfterRead(String string, ExpiringUserAttributes expiringUserAttributes, long currentTime, long currentDuration)
                    {
                        return currentDuration;
                    }
                })
                .build(this::getUserAttributesInternal);

        this.fetchUserAttributesUri = config.getHttpUri()
                .map(uri -> URI.create(uri.toString() + "/fetchUserAttributes"))
                .orElse(null);

        this.retryPolicy = RetryPolicy.builder()
                .handle(IOException.class, RuntimeException.class)
                .withMaxDuration(Duration.ofSeconds(30))
                .withMaxAttempts(MAX_RETRY_ATTEMPTS)
                .withBackoff(RETRY_BACKOFF_INITIAL_DELAY.toMillis(), RETRY_BACKOFF_MAX_DELAY.toMillis(), MILLIS)
                .onRetry(event -> log.warn("Retry attempt %d for external HTTP user attributes: %s",
                        event.getAttemptCount(), event.getLastException().getMessage()))
                .build();

        this.httpAttributeCache = Caffeine.newBuilder()
                .ticker(ticker)
                .expireAfterWrite(config.getHttpCacheTtl().toMillis(), TimeUnit.MILLISECONDS)
                .maximumSize(1000)
                .build();
    }

    @Override
    public Map<String, Object> getUserAttributes(String user, Optional<Principal> principal)
    {
        if (sesBasEnabled) {
            return getBasAttributes(user, principal);
        }
        return getHttpAttributes(user, principal);
    }

    private Map<String, Object> getBasAttributes(String user, Optional<Principal> principal)
    {
        Optional<String> sessionIdOptional = getSessionId(principal);
        if (sessionIdOptional.isEmpty()) {
            log.debug("Session id not found for identity of user %s", user);
            return ImmutableMap.of();
        }
        String sessionId = sessionIdOptional.get();
        try {
            Map<String, Object> attributes = userAttributesCache.get(sessionId).userAttributes();
            if (blpSessionAttributesNode.isPresent()) {
                return ImmutableMap.of(blpSessionAttributesNode.get(), attributes);
            }
            return attributes;
        }
        catch (RuntimeException e) {
            log.warn(e, "Failed to retrieve user attributes from cache for session %s", maskSessionId(sessionId));
            return ImmutableMap.of();
        }
    }

    private Map<String, Object> getHttpAttributes(String user, Optional<Principal> principal)
    {
        // Set the thread context classloader to the plugin's classloader so that the HTTP client
        // can locate service-provider classes (e.g. Jackson modules) loaded by this plugin
        try (ThreadContextClassLoader ignored = new ThreadContextClassLoader(getClass().getClassLoader())) {
            UserAttributeProviderRequest.PrincipalInfo principalInfo = buildPrincipalInfo(principal);
            String authMethod = principalInfo != null ? principalInfo.authMethod() : null;
            String sessionId = principalInfo != null ? principalInfo.sessionId() : null;
            HttpCacheKey cacheKey = new HttpCacheKey(user, authMethod, sessionId);
            return httpAttributeCache.get(cacheKey, _ -> fetchHttpAttributes(user, principalInfo));
        }
        catch (Exception e) {
            log.warn(e, "Failed to retrieve HTTP user attributes for %s", user);
            return ImmutableMap.of();
        }
    }

    private Map<String, Object> fetchHttpAttributes(String user, UserAttributeProviderRequest.PrincipalInfo principalInfo)
    {
        UserAttributeProviderRequest requestBody = new UserAttributeProviderRequest(user, principalInfo);

        Request request = preparePost()
                .setUri(fetchUserAttributesUri)
                .setHeader(CONTENT_TYPE, JSON_UTF_8.toString())
                .setBodyGenerator(jsonBodyGenerator(REQUEST_CODEC, requestBody))
                .build();

        return Failsafe.with(retryPolicy).get(() -> {
            UserAttributeProviderResponse response = httpClient.execute(request, createJsonResponseHandler(RESPONSE_CODEC));
            return ImmutableMap.copyOf(response.attributes());
        });
    }

    @VisibleForTesting
    static UserAttributeProviderRequest.PrincipalInfo buildPrincipalInfo(Optional<Principal> principal)
    {
        if (principal.isEmpty()) {
            return null;
        }

        Principal p = principal.get();
        if (p instanceof DatalakePrincipal datalakePrincipal) {
            return switch (datalakePrincipal) {
                case DatalakeBSSOBloombergPrincipal bssoPrincipal ->
                        new UserAttributeProviderRequest.PrincipalInfo(bssoPrincipal.sessionId().orElse(null), "BSSO");
                case DatalakeLDAPBloombergPrincipal _ ->
                        new UserAttributeProviderRequest.PrincipalInfo(null, "BASIC");
                case DatalakeWAGPrincipal wagPrincipal ->
                        new UserAttributeProviderRequest.PrincipalInfo(wagPrincipal.sessionId().orElse(null), "WAG");
            };
        }

        return new UserAttributeProviderRequest.PrincipalInfo(null, null);
    }

    private Optional<String> getSessionId(Optional<Principal> principal)
    {
        if (principal.isEmpty()) {
            return Optional.empty();
        }
        Principal principalValue = principal.get();
        if (!(principalValue instanceof DatalakePrincipal datalakePrincipal)) {
            return Optional.empty();
        }
        return switch (datalakePrincipal) {
            case DatalakeBSSOBloombergPrincipal datalakeBSSOBloombergPrincipal -> datalakeBSSOBloombergPrincipal.sessionId();
            case DatalakeLDAPBloombergPrincipal _ -> Optional.empty();
            case DatalakeWAGPrincipal wagPrincipal -> wagPrincipal.sessionId();
        };
    }

    private ExpiringUserAttributes getUserAttributesInternal(String sessionId)
    {
        SessionResponse session = sesgetBasClient.retrieveSession(sessionId);
        Map<String, Object> attributes = session
                .getBlocks()
                .stream()
                .map(block -> Map.entry(block.getName(), jsonCodec.fromJson(block.getData())))
                .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
        return new ExpiringUserAttributes(session.getHeader().getExpires(), attributes);
    }

    private static String maskSessionId(String sessionId)
    {
        List<String> sessionSegments = Splitter.on("-").splitToList(sessionId);
        if (sessionSegments.isEmpty() || sessionSegments.getFirst().length() != 32) {
            log.warn("Failed to mask sessionId, format is likely wrong. Falling back to original value");
            return sessionId;
        }
        String firstSegment = sessionSegments.getFirst();
        String markedFirstSegment = firstSegment.substring(0, 12) + Strings.repeat("*", 20);
        return Joiner.on("-").join(Stream.concat(Stream.of(markedFirstSegment), sessionSegments.stream().skip(1)).iterator());
    }

    private record HttpCacheKey(String user, String authMethod, String sessionId) {}

    private record ExpiringUserAttributes(OffsetDateTime expireDateTime, Map<String, Object> userAttributes)
    {
        public ExpiringUserAttributes
        {
            requireNonNull(expireDateTime, "expireDateTime is null");
            requireNonNull(userAttributes, "userAttributes is null");
        }
    }
}

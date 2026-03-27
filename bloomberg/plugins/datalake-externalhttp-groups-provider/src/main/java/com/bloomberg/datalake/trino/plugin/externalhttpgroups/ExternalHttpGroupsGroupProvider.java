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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.log.Logger;
import io.trino.cache.EvictableCacheBuilder;
import io.trino.spi.classloader.ThreadContextClassLoader;
import io.trino.spi.security.GroupProvider;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.json.JsonCodec.jsonCodec;
import static java.time.temporal.ChronoUnit.MILLIS;

/**
 * Group provider that retrieves user groups from an external HTTP endpoint.
 * <p>
 * This implementation includes:
 * <ul>
 *   <li>Automatic retry on failures (max 3 attempts with exponential backoff)</li>
 *   <li>Response caching to reduce HTTP calls</li>
 *   <li>Graceful error handling returning empty sets on failure</li>
 * </ul>
 */
public class ExternalHttpGroupsGroupProvider
        implements GroupProvider
{
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration RETRY_BACKOFF_INITIAL_DELAY = Duration.ofMillis(100);
    private static final Duration RETRY_BACKOFF_MAX_DELAY = Duration.ofSeconds(3);
    private static final Pattern BB_UUID_PATTERN = Pattern.compile("bb_uuid:([0-9]+)");

    private static final Logger log = Logger.get(ExternalHttpGroupsGroupProvider.class);
    private final LoadingCache<String, Set<String>> groupListCache;
    private final URI groupProviderUri;
    private final HttpClient httpClient;
    private final RetryPolicy<Object> retryPolicy;

    @Inject
    ExternalHttpGroupsGroupProvider(ExternalHttpGroupsConfig config, @ForGroupProvider HttpClient client)
    {
        this.groupProviderUri = config.getConfigUri();
        this.httpClient = client;
        this.retryPolicy = RetryPolicy.builder()
                .handle(IOException.class, RuntimeException.class)
                .withMaxDuration(Duration.ofSeconds(30))
                .withMaxAttempts(MAX_RETRY_ATTEMPTS)
                .withBackoff(RETRY_BACKOFF_INITIAL_DELAY.toMillis(), RETRY_BACKOFF_MAX_DELAY.toMillis(), MILLIS)
                .onRetry(event -> log.warn("Retry attempt %d for group provider due to: %s",
                        event.getAttemptCount(), event.getLastException().getMessage()))
                .build();
        this.groupListCache = EvictableCacheBuilder.newBuilder()
                .expireAfterWrite(config.getCacheTTL().toMillis(), TimeUnit.MILLISECONDS)
                .maximumSize(1000)
                .build(CacheLoader.from(this::getUserGroupsFromHttpEndpoints));
    }

    private Set<String> getUserGroupsFromHttpEndpoints(String user)
    {
        Request request = prepareGet()
                .setUri(URI.create(groupProviderUri.toString() + "/getGroups/" + encodeBase64(user)))
                .build();

        try {
            return Failsafe.with(retryPolicy).get(() -> {
                GroupProviderResponse response = httpClient.execute(request, createJsonResponseHandler(jsonCodec(GroupProviderResponse.class)));
                return ImmutableSet.copyOf(response.groupList());
            });
        }
        catch (Exception e) {
            log.error(e, "Failed to retrieve user groups for %s after retries", user);
            return ImmutableSet.of();
        }
    }

    @VisibleForTesting
    protected static String encodeBase64(String input)
    {
        return Base64.getUrlEncoder()
                .encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Set<String> getGroups(String user)
    {
        try (ThreadContextClassLoader ignored = new ThreadContextClassLoader(getClass().getClassLoader())) {
            return groupListCache.getUnchecked(user);
        }
    }
}

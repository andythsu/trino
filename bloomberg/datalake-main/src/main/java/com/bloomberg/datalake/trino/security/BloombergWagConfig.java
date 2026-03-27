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

import io.airlift.configuration.Config;
import io.airlift.units.Duration;
import io.airlift.units.MinDuration;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.net.URI;
import java.util.Optional;

public class BloombergWagConfig
{
    private Optional<URI> wagPublicKeyUri = Optional.empty();
    private Duration publicKeyCacheTTL = Duration.valueOf("1m");
    private int publicKeyFetchMaxRetries = 5;
    private Duration publicKeyFetchRetryInitialDelay = Duration.valueOf("100ms");
    private Duration publicKeyFetchRetryMaxDelay = Duration.valueOf("5s");
    private Duration publicKeyFetchTimeout = Duration.valueOf("10s");

    public Optional<URI> getWagPublicKeyUri()
    {
        return wagPublicKeyUri;
    }

    @Config("bloomberg.wag-public-key.uri")
    public BloombergWagConfig setWagPublicKeyUri(URI wagPublicKeyUri)
    {
        this.wagPublicKeyUri = Optional.ofNullable(wagPublicKeyUri);
        return this;
    }

    @MinDuration("0ms")
    @NotNull
    public Duration getPublicKeyCacheTTL()
    {
        return publicKeyCacheTTL;
    }

    @Config("bloomberg.wag-public-key.cache-ttl")
    public BloombergWagConfig setPublicKeyCacheTTL(Duration publicKeyCacheTTL)
    {
        this.publicKeyCacheTTL = publicKeyCacheTTL;
        return this;
    }

    @Min(0)
    public int getPublicKeyFetchMaxRetries()
    {
        return publicKeyFetchMaxRetries;
    }

    @Config("bloomberg.wag-public-key.fetcher.max-retries")
    public BloombergWagConfig setPublicKeyFetchMaxRetries(int publicKeyFetchMaxRetries)
    {
        this.publicKeyFetchMaxRetries = publicKeyFetchMaxRetries;
        return this;
    }

    @NotNull
    @MinDuration("1ms")
    public Duration getPublicKeyFetchRetryInitialDelay()
    {
        return publicKeyFetchRetryInitialDelay;
    }

    @Config("bloomberg.wag-public-key.fetcher.retry-initial-delay")
    public BloombergWagConfig setPublicKeyFetchRetryInitialDelay(Duration publicKeyFetchRetryInitialDelay)
    {
        this.publicKeyFetchRetryInitialDelay = publicKeyFetchRetryInitialDelay;
        return this;
    }

    @NotNull
    @MinDuration("1ms")
    public Duration getPublicKeyFetchRetryMaxDelay()
    {
        return publicKeyFetchRetryMaxDelay;
    }

    @Config("bloomberg.wag-public-key.fetcher.retry-max-delay")
    public BloombergWagConfig setPublicKeyFetchRetryMaxDelay(Duration publicKeyFetchRetryMaxDelay)
    {
        this.publicKeyFetchRetryMaxDelay = publicKeyFetchRetryMaxDelay;
        return this;
    }

    @NotNull
    @MinDuration("1ms")
    public Duration getPublicKeyFetchTimeout()
    {
        return publicKeyFetchTimeout;
    }

    @Config("bloomberg.wag-public-key.fetcher.timeout")
    public BloombergWagConfig setPublicKeyFetchTimeout(Duration publicKeyFetchTimeout)
    {
        this.publicKeyFetchTimeout = publicKeyFetchTimeout;
        return this;
    }
}

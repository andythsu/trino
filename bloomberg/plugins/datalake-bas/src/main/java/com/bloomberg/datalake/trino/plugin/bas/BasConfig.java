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
package com.bloomberg.datalake.trino.plugin.bas;

import io.airlift.configuration.Config;
import io.airlift.units.Duration;
import io.airlift.units.MinDuration;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Optional;

public class BasConfig
{
    private String metadataFile;
    private String host = "localhost";
    private long retryMaxAttempts = 3;
    private Duration retryBackoff = Duration.valueOf("50ms");
    private Duration blockTimeout = Duration.valueOf("1m");
    private Optional<String> uuidGroupPattern = Optional.empty();
    private long genericUUID;

    @NotNull
    public String getMetadataFile()
    {
        return metadataFile;
    }

    @Config("metadata")
    public BasConfig setMetadataFile(String metadataFile)
    {
        this.metadataFile = metadataFile;
        return this;
    }

    public String getHost()
    {
        return host;
    }

    @Config("host")
    public BasConfig setHost(String host)
    {
        this.host = host;
        return this;
    }

    @Min(0)
    public long getRetryMaxAttempts()
    {
        return retryMaxAttempts;
    }

    @Config("retry-max-attempts")
    public BasConfig setRetryMaxAttempts(long retryMaxAttempts)
    {
        this.retryMaxAttempts = retryMaxAttempts;
        return this;
    }

    @MinDuration("1ms")
    public Duration getRetryBackoff()
    {
        return retryBackoff;
    }

    @Config("retry-backoff")
    public BasConfig setRetryBackoff(Duration retryBackoff)
    {
        this.retryBackoff = retryBackoff;
        return this;
    }

    @MinDuration("1s")
    public Duration getBlockTimeout()
    {
        return blockTimeout;
    }

    @Config("block-timeout")
    public BasConfig setBlockTimeout(Duration blockTimeout)
    {
        this.blockTimeout = blockTimeout;
        return this;
    }

    @NotNull
    public long getGenericUUID()
    {
        return genericUUID;
    }

    @Config("generic-uuid")
    public BasConfig setGenericUUID(long genericUUID)
    {
        this.genericUUID = genericUUID;
        return this;
    }

    @NotNull
    public Optional<String> getUuidGroupPattern()
    {
        return uuidGroupPattern;
    }

    @Config("uuid-group-pattern")
    public BasConfig setUuidGroupPattern(String uuidGroupPattern)
    {
        this.uuidGroupPattern = Optional.ofNullable(uuidGroupPattern);
        return this;
    }
}

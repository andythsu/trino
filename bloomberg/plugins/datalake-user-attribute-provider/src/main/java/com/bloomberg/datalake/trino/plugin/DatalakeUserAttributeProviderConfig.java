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

import com.google.common.net.HostAndPort;
import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;
import io.airlift.units.Duration;
import io.airlift.units.MinDuration;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.net.URI;
import java.util.Optional;

public class DatalakeUserAttributeProviderConfig
{
    private Optional<HostAndPort> basHost = Optional.empty();
    private boolean sesBasEnabled = true;
    private Duration sesgetRetryBackoff = Duration.valueOf("10ms");
    private int sesgetRetries = 3;
    private String blpSessionAttributesNode;
    private URI httpUri;
    private Duration httpCacheTtl = Duration.valueOf("10m");

    @NotNull
    public Optional<HostAndPort> getBasHost()
    {
        return basHost;
    }

    @Config("bas.host")
    public DatalakeUserAttributeProviderConfig setBasHost(HostAndPort basHost)
    {
        this.basHost = Optional.ofNullable(basHost);
        return this;
    }

    public boolean isSesBasEnabled()
    {
        return sesBasEnabled;
    }

    @Config("ses-bas.enabled")
    @ConfigDescription("Enable or disable fetching user attributes via the SES/BAS client")
    public DatalakeUserAttributeProviderConfig setSesBasEnabled(boolean sesBasEnabled)
    {
        this.sesBasEnabled = sesBasEnabled;
        return this;
    }

    @NotNull
    @MinDuration("1ms")
    public Duration getSesgetRetryBackoff()
    {
        return sesgetRetryBackoff;
    }

    @Config("bas.sesget.retry-backoff")
    public DatalakeUserAttributeProviderConfig setSesgetRetryBackoff(Duration sesgetRetryBackoff)
    {
        this.sesgetRetryBackoff = sesgetRetryBackoff;
        return this;
    }

    @Min(0)
    public int getSesgetRetries()
    {
        return sesgetRetries;
    }

    @Config("bas.sesget.retries")
    public DatalakeUserAttributeProviderConfig setSesgetRetries(int sesgetRetries)
    {
        this.sesgetRetries = sesgetRetries;
        return this;
    }

    public Optional<String> getBlpSessionAttributesNode()
    {
        return Optional.ofNullable(blpSessionAttributesNode);
    }

    @Config("bas.blp-session-attributes-node")
    @ConfigDescription("Optional node name to nest all BLP session attributes under a single key")
    public DatalakeUserAttributeProviderConfig setBlpSessionAttributesNode(String blpSessionAttributesNode)
    {
        this.blpSessionAttributesNode = blpSessionAttributesNode;
        return this;
    }

    public Optional<URI> getHttpUri()
    {
        return Optional.ofNullable(httpUri);
    }

    @Config("http.uri")
    @ConfigDescription("Optional URI for external HTTP user attribute endpoint")
    public DatalakeUserAttributeProviderConfig setHttpUri(URI httpUri)
    {
        this.httpUri = httpUri;
        return this;
    }

    @NotNull
    @MinDuration("0s")
    public Duration getHttpCacheTtl()
    {
        return httpCacheTtl;
    }

    @Config("http.cache-ttl")
    @ConfigDescription("Cache TTL for external HTTP user attribute responses. Set to 0s to disable caching")
    public DatalakeUserAttributeProviderConfig setHttpCacheTtl(Duration httpCacheTtl)
    {
        this.httpCacheTtl = httpCacheTtl;
        return this;
    }

    @AssertTrue(message = "http.uri must be configured when ses-bas.enabled is false")
    public boolean isHttpUriConfigValid()
    {
        return sesBasEnabled || httpUri != null;
    }
}

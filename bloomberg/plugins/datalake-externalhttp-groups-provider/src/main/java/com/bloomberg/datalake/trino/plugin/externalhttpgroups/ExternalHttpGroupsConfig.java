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

import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;
import io.airlift.units.Duration;
import jakarta.validation.constraints.NotNull;

import java.net.URI;

public class ExternalHttpGroupsConfig
{
    private URI uri;

    private Duration cacheTTL;

    @NotNull
    public URI getConfigUri()
    {
        return uri;
    }

    @ConfigDescription("Config URI")
    @Config("externalhttpgroups.uri")
    public ExternalHttpGroupsConfig setConfigUri(URI uri)
    {
        this.uri = uri;
        return this;
    }

    @NotNull
    public Duration getCacheTTL()
    {
        return cacheTTL;
    }

    @Config("externalhttpgroups.cache-ttl")
    public ExternalHttpGroupsConfig setCacheTTL(Duration cacheTTL)
    {
        this.cacheTTL = cacheTTL;
        return this;
    }
}

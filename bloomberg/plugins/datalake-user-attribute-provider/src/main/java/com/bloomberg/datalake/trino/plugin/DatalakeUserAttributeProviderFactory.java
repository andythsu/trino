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

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Injector;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.http.client.HttpClient;
import io.airlift.json.JsonModule;
import io.trino.spi.security.UserAttributeProvider;
import io.trino.spi.security.UserAttributeProviderFactory;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class DatalakeUserAttributeProviderFactory
        implements UserAttributeProviderFactory
{
    @Override
    public String getName()
    {
        return "datalake";
    }

    @Override
    public UserAttributeProvider create(Map<String, String> config)
    {
        return create(config, Optional.empty(), Optional.empty());
    }

    @VisibleForTesting
    UserAttributeProvider create(Map<String, String> config, Optional<HttpClient> httpClient)
    {
        return create(config, httpClient, Optional.empty());
    }

    @VisibleForTesting
    UserAttributeProvider create(Map<String, String> config, Optional<HttpClient> httpClient, Optional<Ticker> ticker)
    {
        requireNonNull(config, "config is null");

        Bootstrap app = new Bootstrap(
                new JsonModule(),
                new DatalakeUserAttributeProviderModule(httpClient, ticker));

        Injector injector = app
                .doNotInitializeLogging()
                .setRequiredConfigurationProperties(config)
                .initialize();

        return injector.getInstance(UserAttributeProvider.class);
    }
}

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
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Scopes;
import io.airlift.configuration.AbstractConfigurationAwareModule;
import io.airlift.http.client.HttpClient;
import io.trino.spi.security.UserAttributeProvider;

import java.util.Optional;

import static io.airlift.configuration.ConfigBinder.configBinder;
import static io.airlift.http.client.HttpClientBinder.httpClientBinder;
import static io.airlift.json.JsonCodecBinder.jsonCodecBinder;
import static java.util.Objects.requireNonNull;

public class DatalakeUserAttributeProviderModule
        extends AbstractConfigurationAwareModule
{
    private final Optional<HttpClient> httpClient;
    private final Optional<Ticker> ticker;

    public DatalakeUserAttributeProviderModule()
    {
        this(Optional.empty(), Optional.empty());
    }

    public DatalakeUserAttributeProviderModule(Optional<HttpClient> httpClient)
    {
        this(httpClient, Optional.empty());
    }

    public DatalakeUserAttributeProviderModule(Optional<HttpClient> httpClient, Optional<Ticker> ticker)
    {
        this.httpClient = requireNonNull(httpClient, "httpClient is null");
        this.ticker = requireNonNull(ticker, "ticker is null");
    }

    @Override
    protected void setup(Binder binder)
    {
        configBinder(binder).bindConfig(DatalakeUserAttributeProviderConfig.class);
        jsonCodecBinder(binder).bindMapJsonCodec(String.class, Object.class);
        binder.bind(SesgetBasClient.class).in(Scopes.SINGLETON);
        if (httpClient.isEmpty()) {
            httpClientBinder(binder).bindHttpClient("ForHttpUserAttributeProvider", ForHttpUserAttributeProvider.class);
        }
        else {
            binder.bind(Key.get(HttpClient.class, ForHttpUserAttributeProvider.class)).toInstance(httpClient.orElseThrow());
        }
        binder.bind(Ticker.class).toInstance(ticker.orElse(Ticker.systemTicker()));
        binder.bind(UserAttributeProvider.class).to(DatalakeUserAttributeProvider.class).in(Scopes.SINGLETON);
    }
}

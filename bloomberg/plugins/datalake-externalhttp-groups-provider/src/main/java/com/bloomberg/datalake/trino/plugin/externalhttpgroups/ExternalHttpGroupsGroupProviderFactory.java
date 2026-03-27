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
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Scopes;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.http.client.HttpClient;
import io.trino.spi.security.GroupProvider;
import io.trino.spi.security.GroupProviderFactory;

import java.util.Map;
import java.util.Optional;

import static io.airlift.configuration.ConfigBinder.configBinder;
import static io.airlift.http.client.HttpClientBinder.httpClientBinder;
import static java.util.Objects.requireNonNull;

public class ExternalHttpGroupsGroupProviderFactory
        implements GroupProviderFactory
{
    @Override
    public String getName()
    {
        return "externalhttpgroupprovider";
    }

    @Override
    public GroupProvider create(Map<String, String> config)
    {
        return create(config, Optional.empty());
    }

    @VisibleForTesting
    protected GroupProvider create(Map<String, String> config, Optional<HttpClient> httpClient)
    {
        requireNonNull(config, "config is null");

        Bootstrap app = new Bootstrap(
                binder -> {
                    configBinder(binder).bindConfig(ExternalHttpGroupsConfig.class);
                    Key<HttpClient> httpKey = Key.get(HttpClient.class, ForGroupProvider.class);
                    if (httpClient.isEmpty()) {
                        httpClientBinder(binder).bindHttpClient("ForGroupProvider", ForGroupProvider.class);
                    }
                    else {
                        binder.bind(httpKey).toInstance(httpClient.orElseThrow());
                    }
                    binder.bind(ExternalHttpGroupsGroupProvider.class).in(Scopes.SINGLETON);
                });

        Injector injector = app
                .doNotInitializeLogging()
                .setRequiredConfigurationProperties(config)
                .initialize();

        return injector.getInstance(ExternalHttpGroupsGroupProvider.class);
    }
}

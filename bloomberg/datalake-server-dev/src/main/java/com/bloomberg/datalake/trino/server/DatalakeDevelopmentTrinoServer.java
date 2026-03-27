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
package com.bloomberg.datalake.trino.server;

import com.google.common.collect.ImmutableList;
import com.google.inject.Module;
import com.google.inject.Scopes;
import io.trino.server.DevelopmentLoaderConfig;
import io.trino.server.DevelopmentPluginsProvider;
import io.trino.server.PluginManager.PluginsProvider;
import io.trino.server.Server;

import java.util.List;

import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.configuration.ConfigBinder.configBinder;

public class DatalakeDevelopmentTrinoServer
        extends Server
{
    private DatalakeDevelopmentTrinoServer() {}

    public static void main(String[] args)
    {
        new DatalakeDevelopmentTrinoServer().start("dev");
    }

    @Override
    public List<Module> getAdditionalModules()
    {
        return ImmutableList.of(
                // Development plugin loader
                (binder -> {
                    newOptionalBinder(binder, PluginsProvider.class).setBinding()
                            .to(DevelopmentPluginsProvider.class).in(Scopes.SINGLETON);
                    configBinder(binder).bindConfig(DevelopmentLoaderConfig.class);
                }),
                // Custom Bloomberg module
                new DatalakeModule());
    }
}

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

import com.bloomberg.datalake.trino.plugin.bas.config.BasConfigClient;
import com.bloomberg.datalake.trino.plugin.bas.config.BasServiceConfiguration;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;

import static com.bloomberg.datalake.trino.plugin.bas.jinja.JinjaBinder.jinjaBinder;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static io.airlift.json.JsonCodecBinder.jsonCodecBinder;

public class BasModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.bind(BasConnector.class).in(Scopes.SINGLETON);
        binder.bind(BasMetadata.class).in(Scopes.SINGLETON);
        binder.bind(BasSplitManager.class).in(Scopes.SINGLETON);
        binder.bind(BasRecordSetProvider.class).in(Scopes.SINGLETON);
        binder.bind(BasConfigClient.class).in(Scopes.SINGLETON);
        binder.bind(BasServiceClientProvider.class).in(Scopes.SINGLETON);

        configBinder(binder).bindConfig(BasConfig.class);

        jsonCodecBinder(binder).bindListJsonCodec(BasServiceConfiguration.class);
        jsonCodecBinder(binder).bindMapJsonCodec(String.class, Object.class);

        jinjaBinder(binder).bindFilter(BasOffsetDatetimeFilter.class);
    }
}

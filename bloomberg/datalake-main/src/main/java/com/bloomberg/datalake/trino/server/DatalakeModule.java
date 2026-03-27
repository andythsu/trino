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

import com.bloomberg.datalake.trino.DatalakeErrorLoggerProviderModule;
import com.bloomberg.datalake.trino.security.BloombergWagConfig;
import com.bloomberg.datalake.trino.security.DatalakeOAuth2Authenticator;
import com.bloomberg.datalake.trino.security.DatalakePasswordAuthenticator;
import com.bloomberg.datalake.trino.security.ForWag;
import com.bloomberg.datalake.trino.security.UserHeaderRewriter;
import com.google.inject.Binder;
import com.google.inject.Scopes;
import io.airlift.configuration.AbstractConfigurationAwareModule;
import io.trino.server.security.SecurityConfig;

import static io.airlift.configuration.ConfigBinder.configBinder;
import static io.airlift.http.client.HttpClientBinder.httpClientBinder;

public class DatalakeModule
        extends AbstractConfigurationAwareModule
{
    @Override
    protected void setup(Binder binder)
    {
        configBinder(binder).bindConfig(BloombergWagConfig.class);
        binder.bind(UserHeaderRewriter.class).in(Scopes.SINGLETON);
        httpClientBinder(binder).bindHttpClient("wag", ForWag.class);
        SecurityConfig securityConfig = buildConfigObject(SecurityConfig.class);
        install(DatalakePasswordAuthenticator.module(securityConfig));
        install(DatalakeOAuth2Authenticator.module(securityConfig));
        install(new DatalakeErrorLoggerProviderModule());
    }
}

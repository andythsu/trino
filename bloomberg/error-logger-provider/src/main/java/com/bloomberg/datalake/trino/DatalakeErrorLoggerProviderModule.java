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
package com.bloomberg.datalake.trino;

import com.google.inject.Binder;
import com.google.inject.Scopes;
import io.airlift.configuration.AbstractConfigurationAwareModule;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.log.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.configuration.ConfigurationLoader.loadPropertiesFrom;

public class DatalakeErrorLoggerProviderModule
        extends AbstractConfigurationAwareModule
{
    private final Logger logger = Logger.get(DatalakeErrorLoggerProviderModule.class);
    private static final Path ERROR_LOGGER_PROVIDER_CONFIGURATION = Path.of("etc/datalake-error-logger-provider.properties");

    @Override
    protected void setup(Binder binder)
    {
        newOptionalBinder(binder, ErrorLoggerProvider.class).setDefault().toInstance(ErrorLoggerProvider.NOOP);

        if (!ERROR_LOGGER_PROVIDER_CONFIGURATION.toFile().exists()) {
            return;
        }

        try {
            ConfigurationFactory factory = new ConfigurationFactory(loadPropertiesFrom(ERROR_LOGGER_PROVIDER_CONFIGURATION.toString()));
            DatalakeErrorLoggerProviderConfig config = factory.build(DatalakeErrorLoggerProviderConfig.class);
            binder.bind(DatalakeErrorLoggerProviderConfig.class).toInstance(config);
            newOptionalBinder(binder, ErrorLoggerProvider.class).setBinding().to(DatalakeErrorLoggerProvider.class).in(Scopes.SINGLETON);
            logger.info("DatalakeErrorLoggerProviderModule initialized");
        }
        catch (IOException e) {
            throw new UncheckedIOException("Error reading configuration file " + ERROR_LOGGER_PROVIDER_CONFIGURATION, e);
        }
    }
}

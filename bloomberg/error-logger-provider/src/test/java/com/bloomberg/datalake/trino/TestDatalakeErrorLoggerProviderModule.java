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

import com.google.inject.Injector;
import io.airlift.bootstrap.ApplicationConfigurationException;
import io.airlift.bootstrap.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestDatalakeErrorLoggerProviderModule
{
    private final Path etcDir = Path.of("etc");
    private final Path configFile = etcDir.resolve("datalake-error-logger-provider.properties");

    @Test
    public void testNoConfigProvidesNoOp()
    {
        // when
        Bootstrap app = new Bootstrap(
                new DatalakeErrorLoggerProviderModule());
        Injector injector = app
                .doNotInitializeLogging()
                .initialize();

        // then
        ErrorLoggerProvider provider = injector.getInstance(ErrorLoggerProvider.class);
        assertThat(provider).isSameAs(ErrorLoggerProvider.NOOP);
    }

    @Test
    public void testConfigFileProvidesDatalakeErrorLoggerProvider()
            throws IOException
    {
        // given
        Files.createDirectories(etcDir);
        String configFileContents = "endpoint=https://test.com";
        Files.writeString(configFile, configFileContents);

        // when
        Bootstrap app = new Bootstrap(
                new DatalakeErrorLoggerProviderModule());
        Injector injector = app
                .doNotInitializeLogging()
                .initialize();

        // then
        ErrorLoggerProvider provider = injector.getInstance(ErrorLoggerProvider.class);
        assertThat(provider).isInstanceOf(DatalakeErrorLoggerProvider.class);
        DatalakeErrorLoggerProviderConfig config = injector.getInstance(DatalakeErrorLoggerProviderConfig.class);
        assertThat(config.getEndpoint().toString()).isEqualTo("https://test.com");
    }

    @Test
    public void testEndpointMissingInConfigThrowsException()
            throws IOException
    {
        // given
        Files.createDirectories(etcDir);
        String configFileContents = "";
        Files.writeString(configFile, configFileContents);

        // when
        Bootstrap app = new Bootstrap(
                new DatalakeErrorLoggerProviderModule());

        // then
        assertThatThrownBy(() -> app.doNotInitializeLogging().initialize())
                .isInstanceOf(ApplicationConfigurationException.class)
                .hasMessageContaining("must not be null");
    }

    @BeforeEach
    @AfterEach
    public void cleanupFiles()
            throws IOException
    {
        Files.deleteIfExists(configFile);
        Files.deleteIfExists(etcDir);
    }
}

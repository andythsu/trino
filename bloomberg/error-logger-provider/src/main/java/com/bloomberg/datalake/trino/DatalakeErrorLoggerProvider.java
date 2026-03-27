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

import com.google.inject.Inject;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import jakarta.annotation.PreDestroy;

import java.net.URI;

import static java.util.Objects.requireNonNull;

public class DatalakeErrorLoggerProvider
        implements ErrorLoggerProvider
{
    private final SdkLoggerProvider sdkLoggerProvider;

    @Inject
    public DatalakeErrorLoggerProvider(DatalakeErrorLoggerProviderConfig config)
    {
        requireNonNull(config, "config is null");
        URI endpoint = config.getEndpoint();
        OtlpHttpLogRecordExporter otlpExporter = OtlpHttpLogRecordExporter.builder()
                .setEndpoint(endpoint.toString())
                .build();
        /*
         * DEFAULT_SCHEDULE_DELAY_MILLIS = 1000;
         * DEFAULT_MAX_QUEUE_SIZE = 2048;
         * DEFAULT_MAX_EXPORT_BATCH_SIZE = 512;
         * DEFAULT_EXPORT_TIMEOUT_MILLIS = 30_000;
         */
        this.sdkLoggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(BatchLogRecordProcessor.builder(otlpExporter).build())
                .build();
    }

    public ErrorLogger get(Class<?> clazz)
    {
        return new DatalakeErrorLogger(sdkLoggerProvider.get(clazz.getName()));
    }

    @PreDestroy
    public void shutdown()
    {
        if (sdkLoggerProvider != null) {
            sdkLoggerProvider.close();
        }
    }
}

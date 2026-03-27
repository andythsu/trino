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

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;

import java.lang.reflect.RecordComponent;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.requireNonNull;

public class DatalakeErrorLogger
        implements ErrorLogger
{
    private final io.airlift.log.Logger airliftLogger = io.airlift.log.Logger.get(DatalakeErrorLogger.class);
    private final Logger otelLogger;

    public DatalakeErrorLogger(Logger otelLogger)
    {
        this.otelLogger = otelLogger;
    }

    @Override
    public void log(Severity severity, DatalakeErrorMessage errorMessage, Attributes additionalAttributes)
    {
        LogRecordBuilder builder = otelLogger.logRecordBuilder()
                .setBody(errorMessage.errorMessage())
                .setSeverity(severity)
                .setAllAttributes(additionalAttributes);

        // TODO: using reflection to get the value can be slow.
        //  If this becomes a bottleneck, we need revisit the implementation
        for (RecordComponent component : errorMessage.getClass().getRecordComponents()) {
            LogAttribute annotation = component.getAnnotation(LogAttribute.class);

            if (annotation != null) {
                try {
                    Object value = component.getAccessor().invoke(errorMessage);
                    requireNonNull(value, "LogAttribute annotation value cannot be null");
                    String val = value.toString();
                    if (!isNullOrEmpty(val)) {
                        builder.setAttribute(annotation.value(), value.toString());
                    }
                }
                catch (Exception e) {
                    airliftLogger.error("error getting value from annotation: " + annotation, e);
                }
            }
        }

        builder.emit();
    }
}

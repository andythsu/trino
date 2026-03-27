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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestDatalakeErrorLogger
{
    @Test
    public void testUserFriendlyMessageAndUuidAreAlwaysEmitted()
    {
        // given
        Logger sdkLogger = mock(Logger.class);
        TestingLogRecordBuilder logRecordBuilder = new TestingLogRecordBuilder();
        when(sdkLogger.logRecordBuilder()).thenReturn(logRecordBuilder);

        ErrorLogger errorLogger = new DatalakeErrorLogger(sdkLogger);
        Severity severity = Severity.INFO;
        DatalakeErrorMessage message = DatalakeErrorMessage.errorMessageBuilder()
                .setUserFriendlyMessage("userFriendlyMessage")
                .setRemediationMessage("message")
                .setErrorMessage("message")
                .setUuid("uuid")
                .setQuery("query")
                .build();

        // when
        errorLogger.log(severity, message, Attributes.empty());

        // then
        assertThat(logRecordBuilder.attributes.get(stringKey("user_friendly_message"))).isNotNull();
        assertThat(logRecordBuilder.attributes.get(stringKey("uuid"))).isNotNull();
    }

    static class TestingLogRecordBuilder
            implements LogRecordBuilder
    {
        private final Map<AttributeKey<?>, Object> attributes = new HashMap<>();

        @Override
        public LogRecordBuilder setTimestamp(long l, TimeUnit timeUnit)
        {
            return this;
        }

        @Override
        public LogRecordBuilder setTimestamp(Instant instant)
        {
            return this;
        }

        @Override
        public LogRecordBuilder setObservedTimestamp(long l, TimeUnit timeUnit)
        {
            return this;
        }

        @Override
        public LogRecordBuilder setObservedTimestamp(Instant instant)
        {
            return this;
        }

        @Override
        public LogRecordBuilder setContext(Context context)
        {
            return this;
        }

        @Override
        public LogRecordBuilder setSeverity(Severity severity)
        {
            return this;
        }

        @Override
        public LogRecordBuilder setSeverityText(String s)
        {
            return this;
        }

        @Override
        public LogRecordBuilder setBody(String s)
        {
            return this;
        }

        @Override
        public <T> LogRecordBuilder setAttribute(AttributeKey<T> attributeKey, T t)
        {
            attributes.put(attributeKey, t);
            return this;
        }

        @Override
        public void emit()
        {
            // do nothing
        }
    }
}

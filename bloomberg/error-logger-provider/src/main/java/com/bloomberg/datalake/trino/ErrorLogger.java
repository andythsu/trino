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
import io.opentelemetry.api.logs.Severity;

public interface ErrorLogger
{
    ErrorLogger NOOP = (_, _, _) -> {};

    default void logInfo(DatalakeErrorMessage errorMessage)
    {
        logInfo(errorMessage, Attributes.empty());
    }

    default void logInfo(DatalakeErrorMessage errorMessage, Attributes attributes)
    {
        log(Severity.INFO, errorMessage, attributes);
    }

    void log(Severity severity, DatalakeErrorMessage message, Attributes additionalAttributes);
}

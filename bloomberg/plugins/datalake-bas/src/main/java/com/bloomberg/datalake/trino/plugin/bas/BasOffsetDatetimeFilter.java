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

import com.hubspot.jinjava.interpret.InterpretException;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.lib.filter.Filter;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import static java.lang.String.format;

/**
 * Jinja filter for formatting dates to a string that BAS likes
 */
public class BasOffsetDatetimeFilter
        implements Filter
{
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    @Override
    public Object filter(Object var, JinjavaInterpreter interpreter, String... args)
    {
        if (var == null) {
            return null;
        }

        if (var instanceof OffsetDateTime) {
            return ((OffsetDateTime) var).format(dateTimeFormatter);
        }
        else {
            try {
                return OffsetDateTime.parse(var.toString()).format(dateTimeFormatter);
            }
            catch (Exception e) {
                throw new InterpretException(format("object %s cannot be handled by filter %s", var, getName()), e);
            }
        }
    }

    @Override
    public String getName()
    {
        return "bas_datetime";
    }
}

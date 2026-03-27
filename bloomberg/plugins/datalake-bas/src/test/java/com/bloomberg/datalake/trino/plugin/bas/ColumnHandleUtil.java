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

import java.util.Optional;

import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.VarcharType.createUnboundedVarcharType;

public class ColumnHandleUtil
{
    private ColumnHandleUtil()
    {}

    static BasColumnHandle createVarcharColumn(String name)
    {
        return createVarcharColumn(name, name);
    }

    static BasColumnHandle createVarcharColumn(String name, String jsonKey)
    {
        return new BasColumnHandle(createUnboundedVarcharType(), name, jsonKey, Optional.empty(), Optional.empty());
    }

    static BasColumnHandle createIntegerColumn(String name)
    {
        return createIntegerColumn(name, name);
    }

    static BasColumnHandle createIntegerColumn(String name, String jsonKey)
    {
        return new BasColumnHandle(INTEGER, name, jsonKey, Optional.empty(), Optional.empty());
    }

    static BasColumnHandle createDoubleColumn(String name)
    {
        return createDoubleColumn(name, name);
    }

    static BasColumnHandle createDoubleColumn(String name, String jsonKey)
    {
        return new BasColumnHandle(DOUBLE, name, jsonKey, Optional.empty(), Optional.empty());
    }

    static BasColumnHandle createBooleanColumn(String name)
    {
        return createBooleanColumn(name, name);
    }

    static BasColumnHandle createBooleanColumn(String name, String jsonKey)
    {
        return new BasColumnHandle(BOOLEAN, name, jsonKey, Optional.empty(), Optional.empty());
    }
}

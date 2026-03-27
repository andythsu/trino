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

import com.bloomberg.datalake.trino.plugin.bas.config.BasFunctionConfiguration;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.predicate.TupleDomain;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record BasTableHandle(
        @JsonProperty("tableName") String tableName,
        @JsonProperty("schemaName") String schemaName,
        @JsonProperty("constraint") TupleDomain<ColumnHandle> constraint,
        @JsonProperty("columnHandles") List<BasColumnHandle> columnHandles,
        @JsonProperty("functionMapping") BasFunctionConfiguration functionMapping,
        @JsonProperty("limit") Optional<Long> limit)
        implements ConnectorTableHandle
{
    public BasTableHandle
    {
        requireNonNull(tableName, "tableName is null");
        requireNonNull(schemaName, "schemaName is null");
        requireNonNull(constraint, "constraint is null");
        columnHandles = ImmutableList.copyOf(columnHandles);
        requireNonNull(functionMapping, "functionMapping is null");
        requireNonNull(limit, "limit is null");
    }

    public BasTableHandle(String tableName, String schemaName, BasFunctionConfiguration functionMapping)
    {
        this(tableName, schemaName, TupleDomain.all(), functionMapping.getAllColumns(), functionMapping, Optional.empty());
    }

    public BasTableHandle(String tableName, String schemaName, TupleDomain<ColumnHandle> constraint, List<BasColumnHandle> columnHandles, BasFunctionConfiguration functionMapping)
    {
        this(tableName, schemaName, constraint, columnHandles, functionMapping, Optional.empty());
    }

    public SchemaTableName getSchemaTableName()
    {
        return new SchemaTableName(schemaName, tableName);
    }

    public BasTableHandle withConstraint(TupleDomain<ColumnHandle> newConstraint)
    {
        return new BasTableHandle(tableName, schemaName, newConstraint, columnHandles, functionMapping, limit);
    }

    public BasTableHandle withLimit(long newLimit)
    {
        return new BasTableHandle(tableName, schemaName, constraint, columnHandles, functionMapping, Optional.of(newLimit));
    }
}

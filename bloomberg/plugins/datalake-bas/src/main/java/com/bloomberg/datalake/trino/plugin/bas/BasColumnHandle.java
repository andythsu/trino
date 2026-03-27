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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ColumnMetadata;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.Type;

import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class BasColumnHandle
        implements ColumnHandle
{
    private final String columnName;
    private final Type columnType;
    private final String jsonKey;
    private final Boolean nullable;
    private final Integer maxEntries;

    @JsonCreator
    public BasColumnHandle(
            @JsonProperty("columnType") Type columnType,
            @JsonProperty("columnName") String columnName,
            @JsonProperty("jsonKey") String jsonKey,
            @JsonProperty("nullable") Optional<Boolean> nullable,
            @JsonProperty("maxEntries") Optional<Integer> maxEntries)
    {
        this.columnName = requireNonNull(columnName, "columnName is null");
        this.columnType = requireNonNull(columnType, "columnType is null");
        this.jsonKey = jsonKey;
        this.nullable = nullable.orElse(false);
        this.maxEntries = maxEntries.orElse(null);

        // Make sure that maxEntries is not defined for anything other than ARRAYs (we can support MAPs too in future).
        if (this.maxEntries != null && !(columnType instanceof ArrayType)) {
            String errMsg = String.format("maxEntries cannot be defined for column `%s` of type `%s`", columnName, columnType);
            throw new IllegalArgumentException(errMsg);
        }
    }

    @JsonProperty("nullable")
    public boolean isNullable()
    {
        return nullable;
    }

    @JsonProperty("columnName")
    public String getColumnName()
    {
        return columnName;
    }

    @JsonProperty("columnType")
    public Type getColumnType()
    {
        return columnType;
    }

    @JsonProperty("jsonKey")
    public String getJsonKey()
    {
        return jsonKey;
    }

    @JsonProperty("maxEntries")
    public Integer getMaxEntries()
    {
        return maxEntries;
    }

    public ColumnMetadata getColumnMetadata()
    {
        return getColumnMetadata(false);
    }

    public ColumnMetadata getColumnMetadata(boolean asParameterColumn)
    {
        ColumnMetadata.Builder builder = ColumnMetadata.builder()
                .setName(columnName)
                .setType(columnType)
                .setNullable(isNullable());

        if (asParameterColumn) {
            builder.setExtraInfo(Optional.of(String.format("Parameter with template key: %s", jsonKey)));
        }
        else {
            builder.setExtraInfo(Optional.of(String.format("JSON key: %s", jsonKey)));
        }

        return builder.build();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BasColumnHandle that = (BasColumnHandle) o;
        return Objects.equals(columnName, that.columnName) && Objects.equals(columnType, that.columnType) && Objects.equals(jsonKey, that.jsonKey) && Objects.equals(nullable, that.nullable);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(columnName, columnType, jsonKey, nullable);
    }

    @Override
    public String toString()
    {
        return "BasColumnHandle{" +
                "columnName='" + columnName + '\'' +
                ", columnType=" + columnType +
                ", jsonKey='" + jsonKey + '\'' +
                ", nullable=" + nullable +
                '}';
    }
}

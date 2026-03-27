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
import io.trino.spi.HostAddress;
import io.trino.spi.connector.ConnectorSplit;
import io.trino.spi.connector.SchemaTableName;

import java.util.List;

public class BasSplit
        implements ConnectorSplit
{
    private final SchemaTableName tableName;

    @JsonCreator
    public BasSplit(
            @JsonProperty("tableName") SchemaTableName tableName)
    {
        this.tableName = tableName;
    }

    @JsonProperty("tableName")
    public SchemaTableName getTableName()
    {
        return tableName;
    }

    @Override
    @JsonProperty("isRemotelyAccessible")
    public boolean isRemotelyAccessible()
    {
        return true;
    }

    @Override
    @JsonProperty("getAddresses")
    public List<HostAddress> getAddresses()
    {
        return List.of();
    }
}

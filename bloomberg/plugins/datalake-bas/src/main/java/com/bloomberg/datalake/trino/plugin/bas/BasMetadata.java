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

import com.bloomberg.datalake.trino.plugin.bas.config.BasConfigClient;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ColumnMetadata;
import io.trino.spi.connector.ConnectorMetadata;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.ConnectorTableMetadata;
import io.trino.spi.connector.ConnectorTableProperties;
import io.trino.spi.connector.ConnectorTableVersion;
import io.trino.spi.connector.Constraint;
import io.trino.spi.connector.ConstraintApplicationResult;
import io.trino.spi.connector.LimitApplicationResult;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.connector.SchemaTablePrefix;
import io.trino.spi.expression.Constant;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.TupleDomain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

public class BasMetadata
        implements ConnectorMetadata
{
    private final BasConfigClient config;

    @Inject
    public BasMetadata(BasConfigClient config)
    {
        this.config = config;
    }

    @Override
    public List<String> listSchemaNames(ConnectorSession session)
    {
        return config.getServiceNames();
    }

    @Override
    public ConnectorTableHandle getTableHandle(ConnectorSession session, SchemaTableName tableName,
            Optional<ConnectorTableVersion> startVersion,
            Optional<ConnectorTableVersion> endVersion)
    {
        if (!config.functionExists(tableName)) {
            return null;
        }

        return new BasTableHandle(
                tableName.getTableName(),
                tableName.getSchemaName(),
                config.getFunction(tableName));
    }

    @Override
    public ConnectorTableMetadata getTableMetadata(ConnectorSession session, ConnectorTableHandle table)
    {
        BasTableHandle handle = (BasTableHandle) table;

        return getTableMetadata(handle.getSchemaTableName());
    }

    @Override
    public List<SchemaTableName> listTables(ConnectorSession session, Optional<String> optionalSchemaName)
    {
        List<String> schemaNames = optionalSchemaName.map(List::of).orElse(config.getServiceNames());

        ImmutableList.Builder<SchemaTableName> builder = ImmutableList.builder();
        for (String schemaName : schemaNames.stream().filter(config::serviceExists).collect(toImmutableList())) {
            if (!config.serviceExists(schemaName)) {
                continue;
            }
            for (String tableName : config.getFunctionNames(schemaName)) {
                builder.add(new SchemaTableName(schemaName, tableName));
            }
        }
        return builder.build();
    }

    @Override
    public Map<SchemaTableName, List<ColumnMetadata>> listTableColumns(ConnectorSession session, SchemaTablePrefix prefix)
    {
        requireNonNull(prefix, "prefix is null");
        ImmutableMap.Builder<SchemaTableName, List<ColumnMetadata>> columns = ImmutableMap.builder();
        for (SchemaTableName tableName : config.getFunctionNames(prefix)) {
            ConnectorTableMetadata tableMetadata = getTableMetadata(tableName);
            // table can disappear during listing operation
            if (tableMetadata != null) {
                columns.put(tableName, tableMetadata.getColumns());
            }
        }
        return columns.buildOrThrow();
    }

    @Override
    public Map<String, ColumnHandle> getColumnHandles(ConnectorSession session, ConnectorTableHandle tableHandle)
    {
        BasTableHandle handle = (BasTableHandle) tableHandle;

        return config.getColumnHandles(handle.getSchemaTableName()).stream()
                .collect(toImmutableMap(ch -> ((BasColumnHandle) ch).getColumnName(), identity()));
    }

    @Override
    public ColumnMetadata getColumnMetadata(ConnectorSession session, ConnectorTableHandle tableHandle, ColumnHandle columnHandle)
    {
        var table = (BasTableHandle) tableHandle;
        var column = (BasColumnHandle) columnHandle;

        return table.columnHandles().stream().filter(ch -> ch.equals(column)).findFirst()
                .map(BasColumnHandle::getColumnMetadata)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Column %s is not part of table %s.%s", column.getColumnName(), table.schemaName(), table.tableName())));
    }

    @Override
    public ConnectorTableProperties getTableProperties(ConnectorSession session, ConnectorTableHandle table)
    {
        return new ConnectorTableProperties();
    }

    @Override
    public Optional<ConstraintApplicationResult<ConnectorTableHandle>> applyFilter(ConnectorSession session, ConnectorTableHandle table, Constraint constraint)
    {
        BasTableHandle handle = (BasTableHandle) table;

        TupleDomain<ColumnHandle> oldDomain = handle.constraint();
        TupleDomain<ColumnHandle> newDomain = oldDomain.intersect(constraint.getSummary());

        TupleDomain<ColumnHandle> remainingFilter;
        if (newDomain.isNone()) {
            remainingFilter = TupleDomain.all();
        }
        else {
            var domains = newDomain.getDomains().orElseThrow();
            var columnHandles = domains.keySet().stream().map(BasColumnHandle.class::cast).collect(toImmutableList());

            Map<ColumnHandle, Domain> supported = new HashMap<>();
            Map<ColumnHandle, Domain> unsupported = new HashMap<>();

            for (var col : columnHandles) {
                if (isParameterColumn(handle, col)) {
                    supported.put(col, domains.get(col));
                    unsupported.put(col, Domain.all(col.getColumnType()));
                }
                else {
                    supported.put(col, Domain.all(col.getColumnType()));
                    unsupported.put(col, domains.get(col));
                }
            }

            newDomain = TupleDomain.withColumnDomains(supported);
            remainingFilter = TupleDomain.withColumnDomains(unsupported);
        }

        if (oldDomain.equals(newDomain)) {
            return Optional.empty();
        }

        if (remainingFilter.equals(TupleDomain.none())) {
            remainingFilter = TupleDomain.all();
        }

        return Optional.of(new ConstraintApplicationResult<>(handle.withConstraint(newDomain), remainingFilter, Constant.TRUE, false));
    }

    public Optional<LimitApplicationResult<ConnectorTableHandle>> applyLimit(ConnectorSession session, ConnectorTableHandle table, long limit)
    {
        BasTableHandle handle = (BasTableHandle) table;
        // This function may be called multiple times by the Trino core. We need to indicate that a call has no effect by returning Optional.empty(),
        // otherwise this would loop indefinitely.
        if (handle.limit().map(currentLimit -> currentLimit == limit).orElse(false)) {
            return Optional.empty();
        }
        return Optional.of(new LimitApplicationResult<>(handle.withLimit(limit), false, false));
    }

    private ConnectorTableMetadata getTableMetadata(SchemaTableName table)
    {
        if (!config.functionExists(table)) {
            return null;
        }

        return new ConnectorTableMetadata(table, config.getColumnMetadata(table));
    }

    public boolean isParameterColumn(BasTableHandle tableHandle, ColumnHandle columnHandle)
    {
        return tableHandle.functionMapping().getParameterColumns().stream().anyMatch(pc -> pc.equals(columnHandle));
    }
}

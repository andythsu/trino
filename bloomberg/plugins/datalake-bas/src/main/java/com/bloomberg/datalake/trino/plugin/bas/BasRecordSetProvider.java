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

import com.google.inject.Inject;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorRecordSetProvider;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorSplit;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.ConnectorTransactionHandle;
import io.trino.spi.connector.RecordSet;
import io.trino.spi.security.ConnectorIdentity;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

public class BasRecordSetProvider
        implements ConnectorRecordSetProvider
{
    private final Long genericUUID;
    private final Optional<String> uuidGroupPattern;

    private final BasServiceClientProvider serviceClientProvider;

    @Inject
    public BasRecordSetProvider(
            BasConfig config,
            BasServiceClientProvider serviceClientProvider)
    {
        this.genericUUID = config.getGenericUUID();
        this.uuidGroupPattern = config.getUuidGroupPattern();

        this.serviceClientProvider = requireNonNull(serviceClientProvider, "serviceClientProvider is null");
    }

    @Override
    public RecordSet getRecordSet(ConnectorTransactionHandle transaction, ConnectorSession session, ConnectorSplit split, ConnectorTableHandle table, List<? extends ColumnHandle> columns)
    {
        List<BasColumnHandle> basColumns = columns.stream().map(BasColumnHandle.class::cast).collect(toImmutableList());
        BasTableHandle basTableHandle = (BasTableHandle) table;
        BasServiceClient serviceClient = serviceClientProvider.getClient(basTableHandle.schemaName());

        return new BasRecordSet(session, basTableHandle, basColumns, serviceClient, getUUIDFromIdentity(session.getIdentity()).orElse(genericUUID));
    }

    private Optional<Long> getUUIDFromIdentity(ConnectorIdentity identity)
    {
        if (uuidGroupPattern.isEmpty()) {
            return Optional.empty();
        }

        Pattern pattern = Pattern.compile(uuidGroupPattern.get());
        for (String group : identity.getGroups()) {
            Matcher matcher = pattern.matcher(group);
            if (matcher.find()) {
                return Optional.of(Long.valueOf(matcher.group(1)));
            }
        }

        return Optional.empty();
    }
}

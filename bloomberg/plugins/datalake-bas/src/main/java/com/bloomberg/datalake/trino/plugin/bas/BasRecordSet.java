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

import com.bloomberg.datalake.trino.plugin.bas.errordetection.BasErrorDetector;
import com.bloomberg.datalake.trino.plugin.bas.transforms.BasResponseTransformer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;
import io.trino.spi.TrinoException;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.RecordCursor;
import io.trino.spi.connector.RecordSet;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.EquatableValueSet;
import io.trino.spi.predicate.SortedRangeSet;
import io.trino.spi.type.SqlDate;
import io.trino.spi.type.SqlTimestamp;
import io.trino.spi.type.SqlTimestampWithTimeZone;
import io.trino.spi.type.StandardTypes;
import io.trino.spi.type.Type;
import org.threeten.extra.OffsetDate;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.bloomberg.datalake.trino.plugin.bas.BasErrorCode.BAS_CLIENT_ERROR;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.json.JsonCodec.jsonCodec;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class BasRecordSet
        implements RecordSet
{
    private static final Logger log = Logger.get(BasRecordSet.class);
    private static final JsonCodec<String> STRING_CODEC = jsonCodec(String.class);

    private final ConnectorSession session;
    private final BasServiceClient serviceClient;
    private final BasTableHandle table;
    private final List<BasColumnHandle> columns;
    private final BasResponseTransformer responseTransformer;

    private final Long uuid;

    private static final List<String> directMappingTypeBases = List.of(
            StandardTypes.BIGINT,
            StandardTypes.INTEGER,
            StandardTypes.SMALLINT,
            StandardTypes.TINYINT,
            StandardTypes.DOUBLE,
            StandardTypes.DECIMAL,
            StandardTypes.BOOLEAN,
            StandardTypes.VARCHAR,
            StandardTypes.CHAR,
            StandardTypes.VARBINARY,
            StandardTypes.ARRAY);

    public BasRecordSet(
            ConnectorSession session,
            BasTableHandle table,
            List<BasColumnHandle> columns,
            BasServiceClient serviceClient,
            Long uuid)
    {
        this.session = requireNonNull(session, "session is null");
        this.table = requireNonNull(table, "table is null");
        this.columns = requireNonNull(columns, "columns is null");
        this.serviceClient = requireNonNull(serviceClient, "serviceClient is null");
        this.uuid = requireNonNull(uuid, "uuid is null");
        this.responseTransformer = new BasResponseTransformer(table.functionMapping().getResponseTransforms());
    }

    @Override
    public List<Type> getColumnTypes()
    {
        return columns.stream().map(BasColumnHandle::getColumnType).collect(toImmutableList());
    }

    @Override
    public RecordCursor cursor()
    {
        ImmutableMap.Builder<String, Object> parametersBuilder = parseParametersFromColumns();
        parametersBuilder.put("uuid", uuid);
        table.limit().ifPresent(limit -> parametersBuilder.put("limit", limit));

        Flux<Map<String, Object>> responseFlux = serviceClient.execute(table.tableName(), parametersBuilder.buildOrThrow(), this.uuid);

        Flux<Map<String, Object>> dataFlux = responseFlux.flatMap(response -> {
            log.debug("Received BAS response: %s ", response);

            if (BasErrorDetector.from(table.functionMapping().getBasErrorDetectors()).test(response)) {
                return Flux.error(new TrinoException(BAS_CLIENT_ERROR, format("BAS response error: %s", response)));
            }

            List<Map<String, Object>> data = responseTransformer.transform(response);
            return Flux.fromIterable(data);
        });

        return new BasCursor(columns, dataFlux.toIterable().iterator());
    }

    @VisibleForTesting
    ImmutableMap.Builder<String, Object> parseParametersFromColumns()
    {
        List<BasColumnHandle> parameterColumns = table.functionMapping().getParameterColumns();
        Map<ColumnHandle, Domain> domains = table.constraint().getDomains().orElse(Map.of());

        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        for (BasColumnHandle paramCol : parameterColumns) {
            if (!domains.containsKey(paramCol)) {
                if (paramCol.isNullable()) {
                    continue;
                }
                throw new TrinoException(BasErrorCode.BAS_PARAMETER_NOT_PROVIDED, format("No constraints for parameter column %s", paramCol));
            }

            Domain domain = domains.get(paramCol);

            if (!domain.isSingleValue()) {
                throw new TrinoException(BasErrorCode.BAS_PARAMETER_BAD_VALUE, format("Constraint should be a single value, using the '=' operator for column %s", paramCol));
            }

            Type type = domain.getType();

            Object val;
            // MAPs can't be cast to SortedRangeSet. Handle them separately.
            if (type.getTypeSignature().getBase().equals(StandardTypes.MAP)) {
                // EquatableValueSet has a Set of ValueEntries as opposed to sorted ranges. So, get the first and only
                // entry of the set. Note that, we've already asserted that the domain exactly one value.
                EquatableValueSet valueSet = (EquatableValueSet) domain.getValues();
                Iterator<EquatableValueSet.ValueEntry> it = valueSet.getEntries().iterator();
                val = type.getObjectValue(it.next().getBlock(), 0);
            }
            else {
                SortedRangeSet valueSet = (SortedRangeSet) domain.getValues();
                if (type.getTypeSignature().getBase().equals(StandardTypes.TIMESTAMP)) {
                    SqlTimestamp sqlTimestamp = (SqlTimestamp) type.getObjectValue(valueSet.getSortedRanges(), 0);
                    val = OffsetDateTime.of(sqlTimestamp.toLocalDateTime(), ZoneOffset.UTC);
                }
                else if (type.getTypeSignature().getBase().equals(StandardTypes.TIMESTAMP_WITH_TIME_ZONE)) {
                    SqlTimestampWithTimeZone sqlTimestamp = (SqlTimestampWithTimeZone) type.getObjectValue(valueSet.getSortedRanges(), 0);
                    val = sqlTimestamp.toZonedDateTime().toOffsetDateTime();
                }
                else if (type.getTypeSignature().getBase().equals(StandardTypes.DATE)) {
                    // java-bas-reactor's CodecFactory expects dates to use threeten-extra's OffsetDate, so we parse the LocalDate
                    // provided by Trino into that type
                    SqlDate sqlDate = (SqlDate) type.getObjectValue(valueSet.getSortedRanges(), 0);
                    LocalDate localDate = LocalDate.ofEpochDay(sqlDate.getDays());
                    ZoneOffset offset = session.getTimeZoneKey().getZoneId().getRules().getOffset(localDate.atStartOfDay());
                    val = OffsetDate.of(localDate, offset);
                }
                else if (type.getTypeSignature().getBase().equals(StandardTypes.VARCHAR)) {
                    // JSON Encode string to escape illegal characters
                    String jsonEncodedString = STRING_CODEC.toJson((String) type.getObjectValue(valueSet.getSortedRanges(), 0));
                    // Output of STRING_CODEC is quoted, which we need to remove as we expect the JINJA template to provide the quotes
                    val = jsonEncodedString.substring(1, jsonEncodedString.length() - 1);
                }
                else if (directMappingTypeBases.contains(type.getTypeSignature().getBase())) {
                    val = type.getObjectValue(valueSet.getSortedRanges(), 0);
                }
                else {
                    throw new TrinoException(BasErrorCode.BAS_PARAMETER_UNKNOWN_TYPE, format("Constraint of unknown type %s", type));
                }
            }

            Integer maxEntries = paramCol.getMaxEntries();
            if (maxEntries != null && (val instanceof Collection<?>) && ((Collection<?>) val).size() > maxEntries) {
                String errMsg = String.format("parameter column `%s` cannot contain more than `%d` entries", paramCol.getColumnName(), maxEntries);
                throw new IllegalArgumentException(errMsg);
            }

            builder.put(paramCol.getJsonKey(), val);
        }
        return builder;
    }
}

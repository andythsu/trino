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
import com.bloomberg.datalake.trino.plugin.bas.config.BasResponseTransformsConfig;
import com.bloomberg.datalake.trino.plugin.bas.transforms.BasUnnestTransformAction;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.json.JsonCodec;
import io.airlift.slice.Slices;
import io.trino.spi.TrinoException;
import io.trino.spi.block.Block;
import io.trino.spi.block.IntArrayBlock;
import io.trino.spi.block.MapBlock;
import io.trino.spi.block.SqlMap;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.RecordCursor;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.NullableValue;
import io.trino.spi.predicate.Range;
import io.trino.spi.predicate.SortedRangeSet;
import io.trino.spi.predicate.TupleDomain;
import io.trino.spi.predicate.Utils;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.MapType;
import io.trino.spi.type.Type;
import io.trino.spi.type.TypeOperators;
import io.trino.testing.TestingConnectorSession;
import org.junit.jupiter.api.Test;
import org.threeten.extra.OffsetDate;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.bloomberg.datalake.trino.plugin.bas.BasErrorCode.BAS_PARAMETER_BAD_VALUE;
import static com.bloomberg.datalake.trino.plugin.bas.BasErrorCode.BAS_PARAMETER_NOT_PROVIDED;
import static com.bloomberg.datalake.trino.plugin.bas.MetadataUtil.VARCHARARRAY;
import static io.airlift.json.JsonCodec.mapJsonCodec;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.DateType.DATE;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.TimestampType.TIMESTAMP_MILLIS;
import static io.trino.spi.type.TimestampType.TIMESTAMP_SECONDS;
import static io.trino.spi.type.TimestampWithTimeZoneType.TIMESTAMP_TZ_MILLIS;
import static io.trino.spi.type.VarcharType.createUnboundedVarcharType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestBasRecordSet
{
    private static final Type VARCHAR = createUnboundedVarcharType();
    private static final Type INTARRAY = new ArrayType(INTEGER);
    private static final Type STRINGINTMAP = new MapType(VARCHAR, INTEGER, new TypeOperators());
    private static final Type INTINTMAP = new MapType(INTEGER, INTEGER, new TypeOperators());
    private static final ConnectorSession SESSION = TestingConnectorSession.builder().build();
    private static final JsonCodec<Map<String, Object>> CODEC = mapJsonCodec(String.class, Object.class);

    @Test
    void testGetColumnTypes()
    {
        ImmutableList<BasColumnHandle> columns = ImmutableList.of(
                new BasColumnHandle(BIGINT, "name", "test-key", Optional.empty(), Optional.empty()),
                new BasColumnHandle(INTEGER, "name", "test-key", Optional.empty(), Optional.empty()),
                new BasColumnHandle(VARCHAR, "name", "test-key", Optional.empty(), Optional.empty()),
                new BasColumnHandle(INTARRAY, "name", "test-key", Optional.empty(), Optional.empty()),
                new BasColumnHandle(STRINGINTMAP, "name", "test-key", Optional.empty(), Optional.empty()),
                new BasColumnHandle(DATE, "name", "test-key", Optional.empty(), Optional.empty()));

        BasRecordSet basRecordSet = new BasRecordSet(
                SESSION,
                new BasTableHandle("test", "test", TupleDomain.all(), columns,
                        new BasFunctionConfiguration("test", ImmutableList.of(), columns, "", new BasResponseTransformsConfig(ImmutableList.of()),
                                ImmutableList.of())),
                columns,
                mock(BasServiceClient.class),
                1L);

        assertThat(basRecordSet.getColumnTypes()).isEqualTo(ImmutableList.of(BIGINT, INTEGER, VARCHAR, INTARRAY, STRINGINTMAP, DATE));
    }

    @Test
    void testGetCursorIntegration()
    {
        BasFunctionConfiguration functionConfiguration = new BasFunctionConfiguration(
                "tableName",
                ImmutableList.of(new BasColumnHandle(BIGINT, "col1", "list.key1", Optional.empty(), Optional.empty()),
                        new BasColumnHandle(VARCHAR, "col2", "list.key2", Optional.empty(), Optional.empty()),
                        new BasColumnHandle(DATE, "col3", "list.key3", Optional.empty(), Optional.empty())),
                ImmutableList.of(new BasColumnHandle(VARCHAR, "col4", "template-key", Optional.empty(), Optional.empty()),
                        new BasColumnHandle(DATE, "col5", "date-key", Optional.empty(), Optional.empty())),
                """
                        {"key": "{{ template-key }}", "dateKey": "{{ date-key }}"}""",
                new BasResponseTransformsConfig(ImmutableList.of(new BasUnnestTransformAction("list"))),
                ImmutableList.of());

        Long uuid = 444L;
        String sampleTemplateKey = "value";
        LocalDate sampleDateKey = LocalDate.of(2023, 6, 1);

        List<BasColumnHandle> columns = functionConfiguration.getAllColumns();

        TupleDomain<ColumnHandle> tupleDomain = TupleDomain.fromFixedValues(ImmutableMap.of(
                functionConfiguration.getParameterColumns().get(0), NullableValue.of(VARCHAR, Slices.utf8Slice(sampleTemplateKey)),
                functionConfiguration.getParameterColumns().get(1), NullableValue.of(DATE, sampleDateKey.toEpochDay())));

        BasTableHandle table = new BasTableHandle("tableName", "schemaName", tupleDomain, columns, functionConfiguration);

        BasServiceClient clientMock = mock(BasServiceClient.class);
        Map<String, Object> dummyResponse = CODEC.fromJson("""
                        {
                          "list": [
                            {
                              "key1": 1,
                              "key2": "val_1",
                              "key3": "2023-05-01"
                            },
                            {
                              "key1": 2,
                              "key2": "val_2",
                              "key3": "2023-04-01"
                            }
                          ]
                        }
                """);
        when(clientMock.execute(eq("tableName"), any(), any())).thenReturn(Flux.just(dummyResponse));

        BasRecordSet recordSet = new BasRecordSet(SESSION, table, functionConfiguration.getColumns(), clientMock, uuid);
        RecordCursor cursor = recordSet.cursor();

        verify(clientMock).execute(
                "tableName",
                ImmutableMap.of("template-key", sampleTemplateKey, "date-key", OffsetDate.of(sampleDateKey, ZoneOffset.UTC), "uuid", uuid),
                uuid);

        assertThat(cursor.advanceNextPosition()).isTrue();
        assertThat(cursor.getLong(0)).isEqualTo(1L);
        assertThat(cursor.getSlice(1).toStringUtf8()).isEqualTo("val_1");
        assertThat(LocalDate.parse((String) cursor.getObject(2))).isEqualTo(LocalDate.of(2023, 5, 1));

        assertThat(cursor.advanceNextPosition()).isTrue();
        assertThat(cursor.getLong(0)).isEqualTo(2L);
        assertThat(cursor.getSlice(1).toStringUtf8()).isEqualTo("val_2");
        assertThat(LocalDate.parse((String) cursor.getObject(2))).isEqualTo(LocalDate.of(2023, 4, 1));
    }

    @Test
    void testCorrectParametersForSingleValue()
    {
        LocalDate sampleDate = LocalDate.of(2023, 6, 1);

        assertCorrectParametersForSingleValue(VARCHAR, Slices.utf8Slice("test-value"), "test-value");
        assertCorrectParametersForSingleValue(INTEGER, 1337L, 1337);
        assertCorrectParametersForSingleValue(BIGINT, 1337L, 1337L);
        assertCorrectParametersForSingleValue(DOUBLE, 13.37, 13.37d);
        assertCorrectParametersForSingleValue(BOOLEAN, true, true);
        assertCorrectParametersForSingleValue(DATE, sampleDate.toEpochDay(), OffsetDate.of(sampleDate, ZoneOffset.UTC));  // DATEs are encoded as LONGs in Trino
        assertCorrectParametersForSingleValue(TIMESTAMP_MILLIS, 1643809529071000L, OffsetDateTime.ofInstant(Instant.ofEpochMilli(1643809529071L), ZoneId.of("+00:00")));
        assertCorrectParametersForSingleValue(TIMESTAMP_SECONDS, 1643809529000000L, OffsetDateTime.ofInstant(Instant.ofEpochSecond(1643809529L), ZoneId.of("+00:00")));
        // 1847 America/New_York from zone-index.properties
        assertCorrectParametersForSingleValue(TIMESTAMP_TZ_MILLIS, (1643809529071L << 12) + 1847, OffsetDateTime.ofInstant(Instant.ofEpochMilli(1643809529071L), ZoneId.of(
                "America/New_York")));
    }

    private void assertCorrectParametersForSingleValue(Type type, Object constraintValue, Object expectedParameterValue)
    {
        BasColumnHandle column = new BasColumnHandle(type, "name", "test-key", Optional.empty(), Optional.empty());
        BasRecordSet basRecordSet = createSingleColumnBasRecordSet(column, Domain.singleValue(type, constraintValue));
        Map<String, Object> parameters = basRecordSet.parseParametersFromColumns().buildOrThrow();
        assertThat(parameters).containsAllEntriesOf(ImmutableMap.of("test-key", expectedParameterValue));
    }

    @Test
    void testMultivaluedDomainThrowsTrinoException()
    {
        assertMultivaluedDomainThrowsTrinoException(BIGINT, ImmutableList.of(1337L, 1338L));
        assertMultivaluedDomainThrowsTrinoException(VARCHAR, ImmutableList.of(Slices.utf8Slice("test-value"), Slices.utf8Slice("test-value2")));
        assertMultivaluedDomainThrowsTrinoException(TIMESTAMP_MILLIS, ImmutableList.of(1643809529071000L, 1643809529888000L));
        assertMultivaluedDomainThrowsTrinoException(DATE, ImmutableList.of(LocalDate.of(2023, 6, 1).toEpochDay(), LocalDate.of(2023, 6, 2).toEpochDay()));
    }

    private void assertMultivaluedDomainThrowsTrinoException(Type type, List<Object> values)
    {
        BasColumnHandle column = new BasColumnHandle(type, "name", "test-key", Optional.empty(), Optional.empty());
        BasRecordSet basRecordSet = createSingleColumnBasRecordSet(column, Domain.multipleValues(type, values));
        assertThatThrownBy(basRecordSet::parseParametersFromColumns).isInstanceOfSatisfying(TrinoException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(BAS_PARAMETER_BAD_VALUE.toErrorCode()));
    }

    @Test
    void testRangeDomainThrowsTrinoException()
    {
        BasColumnHandle column = new BasColumnHandle(BIGINT, "name", "test-key", Optional.empty(), Optional.empty());
        BasRecordSet basRecordSet = createSingleColumnBasRecordSet(column, Domain.create(SortedRangeSet.copyOf(BIGINT, ImmutableList.of(Range.greaterThan(BIGINT, 1337L))), false));
        assertThatThrownBy(basRecordSet::parseParametersFromColumns).isInstanceOfSatisfying(TrinoException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(BAS_PARAMETER_BAD_VALUE.toErrorCode()));
    }

    @Test
    void testNoDomainNonNullableThrowsTrinoException()
    {
        BasColumnHandle column = new BasColumnHandle(BIGINT, "name", "test-key", Optional.empty(), Optional.empty());
        BasRecordSet basRecordSet = new BasRecordSet(
                SESSION,
                new BasTableHandle("test", "test", TupleDomain.withColumnDomains(ImmutableMap.of()), ImmutableList.of(column),
                        new BasFunctionConfiguration("test", ImmutableList.of(), ImmutableList.of(column), "", new BasResponseTransformsConfig(ImmutableList.of()),
                                ImmutableList.of())),
                ImmutableList.of(column),
                mock(BasServiceClient.class),
                1L);

        assertThatThrownBy(basRecordSet::parseParametersFromColumns).isInstanceOfSatisfying(TrinoException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(BAS_PARAMETER_NOT_PROVIDED.toErrorCode()));
    }

    @Test
    void testNoDomainNullableDoesntThrowTrinoException()
    {
        BasColumnHandle column = new BasColumnHandle(BIGINT, "name", "test-key", Optional.of(true), Optional.empty());
        BasRecordSet basRecordSet = new BasRecordSet(
                SESSION,
                new BasTableHandle("test", "test", TupleDomain.withColumnDomains(ImmutableMap.of()), ImmutableList.of(column),
                        new BasFunctionConfiguration("test", ImmutableList.of(), ImmutableList.of(column), "", new BasResponseTransformsConfig(ImmutableList.of()),
                                ImmutableList.of())),
                ImmutableList.of(column),
                mock(BasServiceClient.class),
                1L);
        assertThat(basRecordSet.parseParametersFromColumns().buildOrThrow()).isEmpty();
    }

    @Test
    void testCorrectParamsWithMaps()
    {
        // Test 1
        Map<String, Integer> expectedMap1 = ImmutableMap.of("foo", 1);

        MapType strIntMapType = (MapType) STRINGINTMAP;

        int[] offsets1 = {0, 1};
        Block keyBlock1 = Utils.nativeValueToBlock(VARCHAR, Slices.utf8Slice("foo"));
        Block valueBlock1 = Utils.nativeValueToBlock(INTEGER, 1L);

        MapBlock mapBlock1 = strIntMapType.createBlockFromKeyValue(Optional.empty(), offsets1, keyBlock1, valueBlock1);
        SqlMap singleMapBlock1 = strIntMapType.getObject(mapBlock1, 0);

        // Test 2
        Map<Integer, Integer> expectedMap2 = ImmutableMap.of(1, 10, 2, 20, 3, 30);

        IntArrayBlock keyBlock2 = new IntArrayBlock(3, Optional.empty(), new int[] {1, 2, 3});
        IntArrayBlock valueBlock2 = new IntArrayBlock(3, Optional.empty(), new int[] {10, 20, 30});

        int[] positionOffsets = {0, 3};
        MapType intIntType = (MapType) INTINTMAP;
        MapBlock mapBlock2 = intIntType.createBlockFromKeyValue(Optional.empty(), positionOffsets, keyBlock2, valueBlock2);
        SqlMap singleMapBlock2 = intIntType.getObject(mapBlock2, 0);

        assertCorrectParamsWithMaps(STRINGINTMAP, singleMapBlock1, expectedMap1);
        assertCorrectParamsWithMaps(INTINTMAP, singleMapBlock2, expectedMap2);
    }

    private void assertCorrectParamsWithMaps(Type type, Object constraintValue, Object expectedParameterValue)
    {
        BasColumnHandle column = new BasColumnHandle(type, "test-name", "test-key", Optional.empty(), Optional.empty());
        BasRecordSet basRecordSet = createSingleColumnBasRecordSet(column, Domain.singleValue(type, constraintValue));
        Map<String, Object> parameters = basRecordSet.parseParametersFromColumns().buildOrThrow();
        assertThat(parameters).containsAllEntriesOf(ImmutableMap.of("test-key", expectedParameterValue));
    }

    @Test
    void testMaxEntriesTestDataProvider()
    {
        Block arrayBlock1 = new IntArrayBlock(2, Optional.empty(), new int[] {0, 1});
        Block arrayBlock2 = new IntArrayBlock(3, Optional.empty(), new int[] {0, 1, 2});
        Block arrayBlock3 = new IntArrayBlock(4, Optional.empty(), new int[] {0, 1, 2, 3});

        Domain domain1 = Domain.singleValue(INTARRAY, arrayBlock1);
        Domain domain2 = Domain.singleValue(INTARRAY, arrayBlock2);
        Domain domain3 = Domain.singleValue(INTARRAY, arrayBlock3);

        BasColumnHandle column = new BasColumnHandle(VARCHARARRAY, "name", "test-key", Optional.empty(), Optional.of(3));

        assertMaxEntriesRespected(column, domain1, null);
        assertMaxEntriesRespected(column, domain2, null);
        assertMaxEntriesRespected(column, domain3, IllegalArgumentException.class);
    }

    private void assertMaxEntriesRespected(BasColumnHandle column, Domain domain, Class<?> exceptionClass)
    {
        Map<ColumnHandle, Domain> domains = ImmutableMap.of(column, domain);
        BasRecordSet basRecordSet = createSingleColumnBasRecordSet(column, domain);

        if (exceptionClass == null) {
            basRecordSet.parseParametersFromColumns();
        }
        else {
            assertThatThrownBy(basRecordSet::parseParametersFromColumns).isInstanceOf(exceptionClass);
        }
    }

    private static BasRecordSet createSingleColumnBasRecordSet(BasColumnHandle column, Domain domain)
    {
        return new BasRecordSet(
                SESSION,
                createSingleColumnTableHandle(column, domain),
                ImmutableList.of(column),
                mock(BasServiceClient.class),
                1L);
    }

    private static BasTableHandle createSingleColumnTableHandle(BasColumnHandle column, Domain domain)
    {
        return new BasTableHandle("test", "test", TupleDomain.withColumnDomains(ImmutableMap.of(column, domain)), ImmutableList.of(column),
                new BasFunctionConfiguration("test", ImmutableList.of(), ImmutableList.of(column), "", new BasResponseTransformsConfig(ImmutableList.of()),
                        ImmutableList.of()));
    }
}

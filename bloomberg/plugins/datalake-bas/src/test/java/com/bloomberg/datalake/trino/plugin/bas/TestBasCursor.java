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

import io.airlift.slice.Slice;
import io.airlift.slice.Slices;
import io.trino.spi.TrinoException;
import io.trino.spi.type.Type;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.DateType.DATE;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.RealType.REAL;
import static io.trino.spi.type.TimestampType.TIMESTAMP_MILLIS;
import static io.trino.spi.type.TimestampWithTimeZoneType.TIMESTAMP_TZ_MILLIS;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static io.trino.spi.type.VarcharType.createUnboundedVarcharType;
import static java.util.Collections.emptyIterator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestBasCursor
{
    private static final Type UNBOUND_VARCHAR = createUnboundedVarcharType();

    @Test
    void testTypes()
    {
        List<Type> types = List.of(
                VARCHAR,
                INTEGER,
                BIGINT,
                DOUBLE,
                REAL,
                BOOLEAN,
                DATE,
                TIMESTAMP_MILLIS,
                TIMESTAMP_TZ_MILLIS);

        List<BasColumnHandle> columns = types.stream().map(t -> new BasColumnHandle(t, "n", "k", Optional.empty(), Optional.empty())).collect(Collectors.toList());
        BasCursor cursor = new BasCursor(columns, emptyIterator());

        for (int i = 0; i < columns.size(); ++i) {
            assertThat(cursor.getType(i)).isEqualTo(types.get(i));
        }
    }

    @Test
    void testCursorGetData()
    {
        BiFunction<BasCursor, Integer, Long> getLong = BasCursor::getLong;
        BiFunction<BasCursor, Integer, Slice> getSlice = BasCursor::getSlice;
        BiFunction<BasCursor, Integer, Boolean> getBoolean = BasCursor::getBoolean;
        BiFunction<BasCursor, Integer, Double> getDouble = BasCursor::getDouble;

        LocalDate sampleDate = LocalDate.of(2023, 6, 13);

        // LONG
        assertCursorResult(INTEGER, 633, 633L, getLong);
        assertCursorException(INTEGER, 633.5, getLong, ClassCastException.class);
        assertCursorResult(BIGINT, 776, 776L, getLong);
        assertCursorResult(BIGINT, 64L, 64L, getLong);
        assertCursorException(BIGINT, 776.2, getLong, ClassCastException.class);

        // BAS Dates have timezones and Trino encodes DATEs in responses as LONGs.
        assertCursorResult(DATE, String.format("%s+01:00", sampleDate), sampleDate.toEpochDay(), getLong);
        assertCursorResult(DATE, sampleDate.toString(), sampleDate.toEpochDay(), getLong);
        assertCursorException(DATE, "2023-13-05", getLong, TrinoException.class);
        assertCursorException(DATE, "05-13-2023", getLong, TrinoException.class);
        assertCursorException(DATE, "20230613", getLong, TrinoException.class);
        assertCursorException(DATE, "2023/06/13", getLong, TrinoException.class);

        // TIMESTAMPS ARE LONGS
        assertCursorResult(TIMESTAMP_MILLIS, "2022-02-02T13:45:29.071Z", Instant.parse("2022-02-02T13:45:29.071Z").toEpochMilli(), getLong);

        // TIMEZONE KEY 541 -05:00
        assertCursorResult(TIMESTAMP_TZ_MILLIS, "2022-02-02T13:45:29.071-05:00", (ZonedDateTime.parse("2022-02-02T13:45:29.071-05:00").toInstant().toEpochMilli() << 12) + 541, getLong);
        assertCursorException(TIMESTAMP_TZ_MILLIS, "2022-02-02T13:45:29.071", getLong, DateTimeParseException.class);

        // VARCHAR
        assertCursorResult(UNBOUND_VARCHAR, "valuable", Slices.utf8Slice("valuable"), getSlice);

        // BOOLEAN
        assertCursorResult(BOOLEAN, true, true, getBoolean);

        // DOUBLES
        assertCursorResult(DOUBLE, 626.70, 626.70, getDouble);
        assertCursorResult(DOUBLE, 121, 121.0, getDouble);
        assertCursorResult(REAL, 859.67, 859.67, getDouble);
    }

    private static BasCursor createBasCursor(Type type, Object jsonValue)
    {
        List<Map<String, Object>> data = List.of(
                Map.of("name", jsonValue));
        List<BasColumnHandle> columns = List.of(
                new BasColumnHandle(type, "name", "name", Optional.empty(), Optional.empty()));
        BasCursor basCursor = new BasCursor(columns, data.iterator());
        assertThat(basCursor.advanceNextPosition()).isTrue();
        assertThat(basCursor.isNull(0)).isFalse();
        return basCursor;
    }

    private static <T> void assertCursorResult(Type type, Object jsonValue, T expectedValue, BiFunction<BasCursor, Integer, T> func)
    {
        BasCursor basCursor = createBasCursor(type, jsonValue);
        assertThat(func.apply(basCursor, 0)).isEqualTo(expectedValue);
    }

    private static void assertCursorException(Type type, Object jsonValue, BiFunction<BasCursor, Integer, ?> func, Class<? extends Exception> exception)
    {
        BasCursor basCursor = createBasCursor(type, jsonValue);
        assertThatThrownBy(() -> func.apply(basCursor, 0)).isInstanceOf(exception);
    }

    @Test
    void testIsNull()
    {
        List<Map<String, Object>> data = List.of(Map.of());
        List<BasColumnHandle> columns = List.of(
                new BasColumnHandle(UNBOUND_VARCHAR, "name", "name", Optional.of(true), Optional.empty()));
        BasCursor basCursor = new BasCursor(columns, data.iterator());

        assertThat(basCursor.advanceNextPosition()).isTrue();
        assertThat(basCursor.isNull(0)).isTrue();
        assertThat(basCursor.isNull(1)).isTrue();
    }

    @Test
    void testAdvanceNextFalse()
    {
        List<BasColumnHandle> columns = List.of(
                new BasColumnHandle(UNBOUND_VARCHAR, "name", "name", Optional.empty(), Optional.empty()));
        BasCursor basCursor = new BasCursor(columns, emptyIterator());

        assertThat(basCursor.advanceNextPosition()).isFalse();
    }
}

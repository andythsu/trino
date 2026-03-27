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
import io.trino.spi.connector.RecordCursor;
import io.trino.spi.type.DateType;
import io.trino.spi.type.TimestampType;
import io.trino.spi.type.TimestampWithTimeZoneType;
import io.trino.spi.type.Type;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static io.trino.spi.type.DateTimeEncoding.packDateTimeWithZone;

public class BasCursor
        implements RecordCursor
{
    private final List<BasColumnHandle> columns;
    private final Iterator<Map<String, Object>> iterator;

    private Map<String, Object> data;

    public BasCursor(List<BasColumnHandle> columns, Iterator<Map<String, Object>> iterator)
    {
        this.columns = columns;
        this.iterator = iterator;
    }

    @Override
    public long getCompletedBytes()
    {
        return 0;
    }

    @Override
    public long getReadTimeNanos()
    {
        return 0;
    }

    @Override
    public Type getType(int field)
    {
        return columns.get(field).getColumnType();
    }

    @Override
    public boolean advanceNextPosition()
    {
        if (iterator.hasNext()) {
            data = iterator.next();
            return true;
        }
        return false;
    }

    @Override
    public boolean getBoolean(int field)
    {
        return (boolean) getObject(field);
    }

    @Override
    public long getLong(int field)
    {
        Object obj = getObject(field);

        // Trino asks for DATE/TIMESTAMP columns as a LONG value (encoding is specific for each type)
        Type columnType = columns.get(field).getColumnType();

        if (columnType instanceof DateType) {
            String timezoneAwareDate = (String) obj;
            try {
                // BAS returns timezone aware "Date"s of the format YYYY-MM-DD+offset (e.g. 2023-06-05+01:00)
                // We take the part before the offset and parse it to a LocalDate.
                String datePart = timezoneAwareDate.substring(0, 10);
                return LocalDate.parse(datePart).toEpochDay();
            }
            catch (StringIndexOutOfBoundsException | DateTimeParseException exception) {
                String error = String.format("Date `%s` returned by BAS cannot be parsed into a LocalDate.", timezoneAwareDate);
                throw new TrinoException(BasErrorCode.BAS_RESPONSE_BAD_VALUE, error, exception);
            }
        }
        if (columnType instanceof TimestampWithTimeZoneType) {
            ZonedDateTime zonedDateTime = ZonedDateTime.parse((String) obj);
            return packDateTimeWithZone(zonedDateTime.toInstant().toEpochMilli(), zonedDateTime.getZone().getId());
        }
        if (columnType instanceof TimestampType) {
            Instant instant = Instant.parse((String) obj);
            return instant.toEpochMilli();
        }

        if (obj instanceof Integer) {
            return Long.valueOf((Integer) obj);
        }
        return (Long) obj;
    }

    @Override
    public double getDouble(int field)
    {
        Object obj = getObject(field);

        if (obj instanceof Integer) {
            return Double.valueOf((Integer) obj);
        }
        else if (obj instanceof Long) {
            return Double.valueOf((Long) obj);
        }

        return (double) obj;
    }

    @Override
    public Slice getSlice(int field)
    {
        return Slices.utf8Slice(getObject(field).toString());
    }

    @Override
    public Object getObject(int field)
    {
        if (field >= columns.size()) {
            throw new IllegalArgumentException("Field id is outside of column indices");
        }
        Object columnValue = data.get(columns.get(field).getJsonKey());
        if (columnValue == null) {
            throw new IllegalStateException("Attempted to get value for null column");
        }
        return columnValue;
    }

    @Override
    public boolean isNull(int field)
    {
        if (field >= columns.size()) {
            return true;
        }

        return !data.containsKey(columns.get(field).getJsonKey());
    }

    @Override
    public void close()
    {
        return;
    }
}

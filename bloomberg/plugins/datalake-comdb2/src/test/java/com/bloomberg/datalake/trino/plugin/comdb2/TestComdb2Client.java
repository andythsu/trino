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
package com.bloomberg.datalake.trino.plugin.comdb2;

import com.google.common.collect.ImmutableList;
import io.trino.plugin.jdbc.JdbcColumnHandle;
import io.trino.plugin.jdbc.JdbcTableHandle;
import io.trino.plugin.jdbc.JdbcTypeHandle;
import io.trino.plugin.jdbc.RemoteTableName;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.type.StandardTypes;
import io.trino.spi.type.Type;
import io.trino.spi.type.TypeManager;
import io.trino.spi.type.TypeSignature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;

import java.sql.Types;
import java.util.Optional;

import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.DecimalType.createDecimalType;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.RealType.REAL;
import static io.trino.spi.type.SmallintType.SMALLINT;
import static io.trino.spi.type.TimestampWithTimeZoneType.TIMESTAMP_TZ_MILLIS;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static io.trino.testing.TestingSession.testSessionBuilder;
import static io.trino.type.InternalTypeManager.TESTING_TYPE_MANAGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Disabled
@TestInstance(PER_CLASS)
@Execution(SAME_THREAD)
public class TestComdb2Client
{
    private static final ConnectorSession session = testSessionBuilder().build().toConnectorSession();

    private TestingDatabase database;
    private String catalogName;
    private Comdb2Client comdb2Client;
    public static final JdbcTypeHandle JDBC_U_SHORT = new JdbcTypeHandle(Types.SMALLINT, Optional.of("u_short"), Optional.of(2), Optional.of(0), Optional.empty(), Optional.empty());
    public static final JdbcTypeHandle JDBC_SHORT = new JdbcTypeHandle(Types.SMALLINT, Optional.of("short"), Optional.of(2), Optional.of(0), Optional.empty(), Optional.empty());
    public static final JdbcTypeHandle JDBC_INT = new JdbcTypeHandle(Types.INTEGER, Optional.of("int"), Optional.of(4), Optional.of(0), Optional.empty(), Optional.empty());
    public static final JdbcTypeHandle JDBC_U_INT = new JdbcTypeHandle(Types.INTEGER, Optional.of("u_int"), Optional.of(4), Optional.of(0), Optional.empty(), Optional.empty());
    public static final JdbcTypeHandle JDBC_LONGLONG = new JdbcTypeHandle(Types.BIGINT, Optional.of("longlong"), Optional.of(8), Optional.of(0), Optional.empty(), Optional.empty());

    public static final JdbcTypeHandle JDBC_FLOAT = new JdbcTypeHandle(Types.REAL, Optional.of("float"), Optional.of(4), Optional.of(0), Optional.empty(), Optional.empty());
    public static final JdbcTypeHandle JDBC_DOUBLE = new JdbcTypeHandle(Types.DOUBLE, Optional.of("double"), Optional.of(8), Optional.of(0), Optional.empty(), Optional.empty());

    public static final JdbcTypeHandle JDBC_VARCHAR = new JdbcTypeHandle(Types.VARCHAR, Optional.of("cstring"), Optional.of(10), Optional.of(0), Optional.empty(), Optional.empty());
    public static final JdbcTypeHandle JDBC_BYTE = new JdbcTypeHandle(Types.VARCHAR, Optional.of("byte"), Optional.of(1024), Optional.of(0), Optional.empty(), Optional.empty());
    public static final JdbcTypeHandle JDBC_BLOB = new JdbcTypeHandle(Types.VARCHAR, Optional.of("blob"), Optional.of(1024), Optional.of(0), Optional.empty(), Optional.empty());

    public static final JdbcTypeHandle JDBC_DECIMAL32 = new JdbcTypeHandle(Types.DECIMAL, Optional.of("decimal32"), Optional.of(6), Optional.of(0), Optional.empty(), Optional.empty());
    public static final JdbcTypeHandle JDBC_DECIMAL64 = new JdbcTypeHandle(Types.DECIMAL, Optional.of("decimal64"), Optional.of(12), Optional.of(0), Optional.empty(), Optional.empty());
    public static final JdbcTypeHandle JDBC_DECIMAL128 = new JdbcTypeHandle(Types.DECIMAL, Optional.of("decimal128"), Optional.of(21), Optional.of(0), Optional.empty(), Optional.empty());

    public static final JdbcTypeHandle JDBC_DATETIME = new JdbcTypeHandle(Types.TIMESTAMP_WITH_TIMEZONE, Optional.of("datetime"), Optional.of(10), Optional.of(0), Optional.empty(), Optional.empty());
    public static final JdbcTypeHandle JDBC_DATETIMEUS = new JdbcTypeHandle(Types.TIMESTAMP_WITH_TIMEZONE, Optional.of("datetimeus"), Optional.of(10), Optional.of(0), Optional.empty(), Optional.empty());
    public static final JdbcTypeHandle JDBC_INTERVALYM = new JdbcTypeHandle(1111, Optional.of("intervalym"), Optional.of(10), Optional.of(0), Optional.empty(), Optional.empty());
    public static final JdbcTypeHandle JDBC_INTERVALDS = new JdbcTypeHandle(1111, Optional.of("intervalds"), Optional.of(8), Optional.of(0), Optional.empty(), Optional.empty());

    private static final TypeManager TYPE_MANAGER = TESTING_TYPE_MANAGER;

    private static final Type INTERVAL_DAY_TIME = TYPE_MANAGER.getType(new TypeSignature(StandardTypes.INTERVAL_DAY_TO_SECOND));
    private static final Type INTERVAL_YEAR_MONTH = TYPE_MANAGER.getType(new TypeSignature(StandardTypes.INTERVAL_YEAR_TO_MONTH));

    @BeforeAll
    public void setUp()
            throws Exception
    {
        database = new TestingDatabase();
        catalogName = database.getConnection().getCatalog();
        comdb2Client = database.getComdb2Client();
    }

    @AfterAll
    public void tearDown()
            throws Exception
    {
        database.close();
    }

    // todo: type-matching not working? Testing will error for clear mismatches but not point to type error
    //  Bounded varchars not implemented
    @Test
    public void testMetadata()
    {
        SchemaTableName schemaTableName = new SchemaTableName("dev-10-178-5-252.ob1.bcc.bloomberg.com", "datatypetest");
        Optional<JdbcTableHandle> table = comdb2Client.getTableHandle(session, schemaTableName);
        assertThat(table.isPresent()).isTrue();
        assertThat(table.get().getRequiredNamedRelation().getRemoteTableName().getCatalogName().get()).isEqualTo(catalogName);
        assertThat(table.get().getRequiredNamedRelation().getSchemaTableName().getSchemaName()).isEqualTo("dev-10-178-5-252.ob1.bcc.bloomberg.com");
        assertThat(table.get().getRequiredNamedRelation().getSchemaTableName().getTableName()).isEqualTo("datatypetest");
        assertThat(table.get().getRequiredNamedRelation().getRemoteTableName().getTableName()).isEqualTo("datatypetest");
        assertThat(table.get().getRequiredNamedRelation().getSchemaTableName()).isEqualTo(schemaTableName);
        RemoteTableName remoteTableName = table.get().getRequiredNamedRelation().getRemoteTableName();
        assertThat(comdb2Client.getColumns(session, schemaTableName, remoteTableName)).isEqualTo(ImmutableList.of(
                new JdbcColumnHandle("type", JDBC_VARCHAR, VARCHAR),
                new JdbcColumnHandle("double_val", JDBC_DOUBLE, DOUBLE),
                new JdbcColumnHandle("interval_year_month", JDBC_INTERVALYM, INTERVAL_YEAR_MONTH),
                new JdbcColumnHandle("interval_day_second", JDBC_INTERVALDS, INTERVAL_DAY_TIME),
                new JdbcColumnHandle("interval_day_second_us", JDBC_INTERVALDS, INTERVAL_DAY_TIME),
                new JdbcColumnHandle("date_time", JDBC_DATETIME, TIMESTAMP_TZ_MILLIS),
                new JdbcColumnHandle("date_time_us", JDBC_DATETIME, TIMESTAMP_TZ_MILLIS),
                new JdbcColumnHandle("decimal_small", JDBC_DECIMAL32, createDecimalType(6)),
                new JdbcColumnHandle("decimal_medium", JDBC_DECIMAL64, createDecimalType(12)),
                new JdbcColumnHandle("decimal_large", JDBC_DECIMAL128, createDecimalType(21)),
                new JdbcColumnHandle("byte_val", JDBC_BYTE, VARCHAR),
                new JdbcColumnHandle("blob_val", JDBC_BLOB, VARCHAR),
                new JdbcColumnHandle("small_int", JDBC_SHORT, SMALLINT),
                new JdbcColumnHandle("u_small_int", JDBC_U_SHORT, SMALLINT),
                new JdbcColumnHandle("integer_val", JDBC_INT, INTEGER),
                new JdbcColumnHandle("u_integer_val", JDBC_U_INT, INTEGER),
                new JdbcColumnHandle("big_int_val", JDBC_LONGLONG, BIGINT)));
    }

    @Test
    public void testMetadataWithByte()
    {
        SchemaTableName schemaTableName = new SchemaTableName("dev-10-178-5-252.ob1.bcc.bloomberg.com", "sqlite_stat4");
        Optional<JdbcTableHandle> table = comdb2Client.getTableHandle(session, schemaTableName);
        assertThat(table.isPresent()).isTrue();
        RemoteTableName remoteTableName = table.get().getRequiredNamedRelation().getRemoteTableName();
        assertThat(comdb2Client.getColumns(session, schemaTableName, remoteTableName)).isEqualTo(ImmutableList.of(
                new JdbcColumnHandle("tbl", JDBC_VARCHAR, VARCHAR),
                new JdbcColumnHandle("idx", JDBC_VARCHAR, VARCHAR),
                new JdbcColumnHandle("samplelen", JDBC_INT, INTEGER),
                new JdbcColumnHandle("sample", JDBC_BYTE, VARCHAR)));
    }

    @Test
    public void testMetadataWithSystemTables()
    {
        SchemaTableName schemaTableName = new SchemaTableName("dev-10-178-5-252.ob1.bcc.bloomberg.com", "comdb2_type_samples");
        comdb2Client.setSystemTables(true);
        Optional<JdbcTableHandle> table = comdb2Client.getTableHandle(session, schemaTableName);
        RemoteTableName remoteTableName = table.get().getRequiredNamedRelation().getRemoteTableName();
        assertThat(comdb2Client.getColumns(session, schemaTableName, remoteTableName)).isEqualTo(ImmutableList.of(
                new JdbcColumnHandle("integer", JDBC_INT, BIGINT),
                new JdbcColumnHandle("real", JDBC_FLOAT, REAL),
                new JdbcColumnHandle("cstring", JDBC_VARCHAR, VARCHAR),
                new JdbcColumnHandle("blob", JDBC_BLOB, VARCHAR),
                new JdbcColumnHandle("datetime", JDBC_DATETIME, TIMESTAMP_TZ_MILLIS),
                new JdbcColumnHandle("intervalym", JDBC_INTERVALYM, INTERVAL_YEAR_MONTH),
                new JdbcColumnHandle("intervalds", JDBC_INTERVALDS, INTERVAL_DAY_TIME),
                new JdbcColumnHandle("datetimeus", JDBC_DATETIMEUS, TIMESTAMP_TZ_MILLIS),
                new JdbcColumnHandle("intervaldsus", JDBC_INTERVALDS, INTERVAL_YEAR_MONTH)));
        testMetadata();
    }
}

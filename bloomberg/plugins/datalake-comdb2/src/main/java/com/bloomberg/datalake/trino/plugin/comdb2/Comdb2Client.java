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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.trino.client.IntervalDayTime;
import io.trino.client.IntervalYearMonth;
import io.trino.plugin.base.aggregation.AggregateFunctionRewriter;
import io.trino.plugin.base.aggregation.AggregateFunctionRule;
import io.trino.plugin.base.expression.ConnectorExpressionRewriter;
import io.trino.plugin.base.mapping.IdentifierMapping;
import io.trino.plugin.base.mapping.RemoteIdentifiers;
import io.trino.plugin.jdbc.BaseJdbcClient;
import io.trino.plugin.jdbc.BaseJdbcConfig;
import io.trino.plugin.jdbc.ColumnMapping;
import io.trino.plugin.jdbc.ConnectionFactory;
import io.trino.plugin.jdbc.JdbcColumnHandle;
import io.trino.plugin.jdbc.JdbcExpression;
import io.trino.plugin.jdbc.JdbcSplit;
import io.trino.plugin.jdbc.JdbcTableHandle;
import io.trino.plugin.jdbc.JdbcTypeHandle;
import io.trino.plugin.jdbc.LongWriteFunction;
import io.trino.plugin.jdbc.QueryBuilder;
import io.trino.plugin.jdbc.RemoteTableName;
import io.trino.plugin.jdbc.UnsupportedTypeHandling;
import io.trino.plugin.jdbc.WriteMapping;
import io.trino.plugin.jdbc.aggregation.ImplementAvgDecimal;
import io.trino.plugin.jdbc.aggregation.ImplementAvgFloatingPoint;
import io.trino.plugin.jdbc.aggregation.ImplementCount;
import io.trino.plugin.jdbc.aggregation.ImplementCountAll;
import io.trino.plugin.jdbc.aggregation.ImplementMinMax;
import io.trino.plugin.jdbc.aggregation.ImplementSum;
import io.trino.plugin.jdbc.expression.JdbcConnectorExpressionRewriterBuilder;
import io.trino.plugin.jdbc.expression.ParameterizedExpression;
import io.trino.plugin.jdbc.logging.RemoteQueryModifier;
import io.trino.spi.TrinoException;
import io.trino.spi.connector.AggregateFunction;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.connector.TableNotFoundException;
import io.trino.spi.predicate.Domain;
import io.trino.spi.security.ConnectorIdentity;
import io.trino.spi.type.CharType;
import io.trino.spi.type.DecimalType;
import io.trino.spi.type.StandardTypes;
import io.trino.spi.type.Type;
import io.trino.spi.type.TypeManager;
import io.trino.spi.type.TypeSignature;
import io.trino.spi.type.VarcharType;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.getOnlyElement;
import static io.trino.plugin.jdbc.JdbcErrorCode.JDBC_ERROR;
import static io.trino.plugin.jdbc.StandardColumnMappings.bigintColumnMapping;
import static io.trino.plugin.jdbc.StandardColumnMappings.charWriteFunction;
import static io.trino.plugin.jdbc.StandardColumnMappings.defaultVarcharColumnMapping;
import static io.trino.plugin.jdbc.StandardColumnMappings.doubleColumnMapping;
import static io.trino.plugin.jdbc.StandardColumnMappings.integerColumnMapping;
import static io.trino.plugin.jdbc.StandardColumnMappings.longDecimalWriteFunction;
import static io.trino.plugin.jdbc.StandardColumnMappings.realColumnMapping;
import static io.trino.plugin.jdbc.StandardColumnMappings.shortDecimalWriteFunction;
import static io.trino.plugin.jdbc.StandardColumnMappings.smallintColumnMapping;
import static io.trino.plugin.jdbc.StandardColumnMappings.varbinaryColumnMapping;
import static io.trino.plugin.jdbc.StandardColumnMappings.varcharWriteFunction;
import static io.trino.plugin.jdbc.TypeHandlingJdbcSessionProperties.getUnsupportedTypeHandling;
import static io.trino.plugin.jdbc.UnsupportedTypeHandling.IGNORE;
import static io.trino.spi.StandardErrorCode.NOT_SUPPORTED;
import static io.trino.spi.type.DateTimeEncoding.packDateTimeWithZone;
import static io.trino.spi.type.DateTimeEncoding.unpackMillisUtc;
import static io.trino.spi.type.TimestampWithTimeZoneType.TIMESTAMP_TZ_MILLIS;
import static java.lang.String.format;
import static java.sql.DatabaseMetaData.columnNoNulls;

public class Comdb2Client
        extends BaseJdbcClient
{
    private static final Joiner DOT_JOINER = Joiner.on(".");
    private static final Logger log = Logger.get(Comdb2Client.class);
    private static final int MAX_LIST_EXPRESSIONS = 500;
    private static Type intervalDayTimeType;
    private static Type intervalYearMonthType;
    private boolean isSystem;
    private boolean system;
    private boolean resolveVutf8ColumnSizes;
    private final AggregateFunctionRewriter aggregateFunctionRewriter;
    private final ConnectorExpressionRewriter<ParameterizedExpression> connectorExpressionRewriter;

    private static final Function<String, String> maxLengthFormatter = (s) -> format("MAX(LENGTH(%s)) as %s", s, s);

    // TODO improve this by calling Domain#simplify
    private static final UnaryOperator<Domain> DISABLE_UNSUPPORTED_PUSHDOWN = domain -> {
        if (domain.getValues().getRanges().getRangeCount() <= MAX_LIST_EXPRESSIONS) {
            return domain;
        }
        return Domain.all(domain.getType());
    };

    @Inject
    public Comdb2Client(BaseJdbcConfig config,
                        ConnectionFactory connectionFactory,
                        Comdb2Config comdb2Config,
                        QueryBuilder queryBuilder,
                        TypeManager typeManager,
                        IdentifierMapping identifierMapping,
                        RemoteQueryModifier remoteQueryModifier)
    {
        super("\"", connectionFactory, queryBuilder, config.getJdbcTypesMappedToVarchar(), identifierMapping, remoteQueryModifier, false);
        if (comdb2Config.isIncludeSystemTables()) {
            isSystem = true;
        }
        else {
            isSystem = false;
        }
        system = isSystem;
        this.intervalYearMonthType = typeManager.getType(new TypeSignature(StandardTypes.INTERVAL_YEAR_TO_MONTH));
        this.intervalDayTimeType = typeManager.getType(new TypeSignature(StandardTypes.INTERVAL_DAY_TO_SECOND));

        this.resolveVutf8ColumnSizes = comdb2Config.isResolveVutf8ColumnSizes();

        JdbcTypeHandle bigintTypeHandle = new JdbcTypeHandle(Types.BIGINT, Optional.of("u_int"), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        this.connectorExpressionRewriter = JdbcConnectorExpressionRewriterBuilder.newBuilder()
            .addStandardRules(this::quoted)
            .build();

        this.aggregateFunctionRewriter = new AggregateFunctionRewriter<>(
                this.connectorExpressionRewriter,
                ImmutableSet.<AggregateFunctionRule<JdbcExpression, ParameterizedExpression>>builder()
                        .add(new ImplementCountAll(bigintTypeHandle))
                        .add(new ImplementCount(bigintTypeHandle))
                        .add(new ImplementMinMax(true))
                        .add(new ImplementSum(Comdb2Client::toTypeHandle))
                        .add(new ImplementAvgFloatingPoint())
                        .add(new ImplementAvgDecimal())
                        .build());
    }

    @Override
    public Collection<String> listSchemas(Connection connection)
    {
        system = isSystem;
        try (ResultSet resultSet = connection.getMetaData().getSchemas()) {
            ImmutableSet.Builder<String> schemaNames = ImmutableSet.builder();
            schemaNames.add(connection.getSchema());
            while (resultSet.next()) {
                if (resultSet.getString("TABLE_SCHEM") != null) {
                    String schemaName = resultSet.getString("TABLE_SCHEM");
                    // skip internal schemas
                    if (!schemaName.equalsIgnoreCase("information_schema") && schemaName != null) {
                        schemaNames.add(schemaName);
                    }
                }
            }
            return schemaNames.build();
        }
        catch (SQLException e) {
            throw new TrinoException(JDBC_ERROR, e);
        }
    }

    @Override
    public Optional<JdbcTableHandle> getTableHandle(ConnectorSession session, SchemaTableName schemaTableName)
    {
        try (Connection connection = connectionFactory.openConnection(session)) {
            ConnectorIdentity connectorIdentity = session.getIdentity();
            RemoteIdentifiers remoteIdentifiers = getRemoteIdentifiers(connection);
            String remoteSchema = getIdentifierMapping().toRemoteSchemaName(remoteIdentifiers, connectorIdentity, schemaTableName.getSchemaName());
            String remoteTable = getIdentifierMapping().toRemoteTableName(remoteIdentifiers, connectorIdentity, remoteSchema, schemaTableName.getTableName());
            try (ResultSet resultSet = getTables(connection, Optional.of(remoteTable))) {
                List<JdbcTableHandle> tableHandles = new ArrayList<>();
                while (resultSet.next()) {
                    if (system) {
                        tableHandles.add(new JdbcTableHandle(
                                schemaTableName,
                                new RemoteTableName(
                                        Optional.ofNullable(connection.getCatalog()),
                                        Optional.empty(),
                                        resultSet.getString("NAME")),
                                getTableComment(resultSet)));
                    }
                    else {
                        tableHandles.add(new JdbcTableHandle(
                                schemaTableName,
                                new RemoteTableName(
                                        Optional.ofNullable(resultSet.getString("TABLE_CAT")),
                                        Optional.ofNullable(resultSet.getString("TABLE_SCHEM")),
                                        resultSet.getString("TABLE_NAME")),
                                getTableComment(resultSet)));
                    }
                }
                if (tableHandles.isEmpty()) {
                    return Optional.empty();
                }
                if (tableHandles.size() > 1) {
                    throw new TrinoException(NOT_SUPPORTED, "Multiple tables matched: " + schemaTableName);
                }
                return Optional.of(getOnlyElement(tableHandles));
            }
        }
        catch (SQLException e) {
            throw new TrinoException(JDBC_ERROR, e);
        }
    }

    @Override
    public List<SchemaTableName> getTableNames(ConnectorSession session, Optional<String> schema)
    {
        system = isSystem;
        try (Connection connection = connectionFactory.openConnection(session)) {
            ConnectorIdentity identity = session.getIdentity();
            try (ResultSet resultSet = getTables(connection, Optional.empty())) {
                ImmutableList.Builder<SchemaTableName> list = ImmutableList.builder();
                while (resultSet.next()) {
                    String tableSchema = connection.getSchema();
                    String tableName;
                    tableName = resultSet.getString("TABLE_NAME");
                    list.add(new SchemaTableName(tableSchema, tableName));
                }
                if (system) {
                    try (ResultSet rs = getSystemTables(connection, Optional.empty())) {
                        while (rs.next()) {
                            String tableSchema = connection.getSchema();
                            String tableName;
                            tableName = rs.getString("NAME");
                            list.add(new SchemaTableName(tableSchema, tableName));
                        }
                    }
                }
                return list.build();
            }
        }
        catch (SQLException e) {
            throw new TrinoException(JDBC_ERROR, e);
        }
    }

    protected ResultSet getSystemTables(Connection connection, Optional<String> tableName)
            throws SQLException
    {
        if (tableName.isPresent()) {
            return connection.createStatement().executeQuery("select * from comdb2_systables where name='" + tableName.get() + "'");
        }
        else {
            return connection.createStatement().executeQuery("select name from comdb2_systables");
        }
    }

    protected ResultSet getNonSystemTables(Connection connection, Optional<String> tableName)
            throws SQLException
    {
        DatabaseMetaData metadata = connection.getMetaData();
        return metadata.getTables(connection.getCatalog(),
                null,
                tableName.orElse(null),
                new String[] {"TABLE", "VIEWS"});
    }

    @Override
    public ResultSet getTables(Connection connection, Optional<String> schemaName, Optional<String> tableName)
            throws SQLException
    {
        return getTables(connection, tableName);
    }

    protected ResultSet getTables(Connection connection, Optional<String> tableName)
            throws SQLException
    {
        system = isSystem;
        ResultSet rs;
        rs = getNonSystemTables(connection, tableName);
        if (!rs.next() && system) {
            rs = getSystemTables(connection, tableName);
        }
        else {
            rs = getNonSystemTables(connection, tableName);
            if (tableName.isPresent()) {
                system = false;
            }
        }
        return rs;
    }

    private Map<String, Integer> resolveVutf8ColumnSizes(ConnectorSession session, RemoteTableName remoteTableName)
            throws SQLException
    {
        Map<String, Integer> vutf8ColumnSizes = new HashMap<>();
        if (!resolveVutf8ColumnSizes) {
            return vutf8ColumnSizes;
        }

        Connection connection = connectionFactory.openConnection(session);
        // This query gets all the columns in the table that are of type vutf8
        // and do not have a column size specified in the comdb2 table schema
        PreparedStatement ps = connection.prepareStatement("SELECT columnname FROM comdb2sys_columns WHERE tablename = ? AND type = 'vutf8' AND varinlinesize = 0");
        ps.setString(1, remoteTableName.getTableName());

        List<String> vutf8Columns = new ArrayList<>();
        ResultSet resultSet = ps.executeQuery();
        while (resultSet.next()) {
            String columnName = resultSet.getString("columnname");
            vutf8Columns.add(columnName);
        }
        if (vutf8Columns.isEmpty()) {
            return vutf8ColumnSizes;
        }

        String maxLengthClause = String.join(",", vutf8Columns.stream().map(maxLengthFormatter).collect(Collectors.toList()));
        String vutf8ColumnSizesSQL = "SELECT " + maxLengthClause + " FROM " + remoteTableName + " LIMIT 1";

        ResultSet vutf8ColumnSizesRS = connection.createStatement().executeQuery(vutf8ColumnSizesSQL);
        if (vutf8ColumnSizesRS.next()) {
            ResultSetMetaData rsmd = vutf8ColumnSizesRS.getMetaData();
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                vutf8ColumnSizes.put(rsmd.getColumnName(i), vutf8ColumnSizesRS.getInt(i));
            }
        }
        return vutf8ColumnSizes;
    }

    @Override
    public List<JdbcColumnHandle> getColumns(ConnectorSession session, SchemaTableName schemaTableName, RemoteTableName remoteTableName)
    {
        try (Connection connection = connectionFactory.openConnection(session)) {
            ResultSet resultSet;
            int allColumns = 0;
            List<JdbcColumnHandle> columns = new ArrayList<>();
            if (system) {
                //todo: column size inaccurate for system tables
                resultSet = connection.createStatement().executeQuery("select * from " + remoteTableName + " limit 1");
                ResultSetMetaData rsmd = resultSet.getMetaData();
                for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                    allColumns++;
                    String columnName = rsmd.getColumnName(i);
                    JdbcTypeHandle typeHandle = new JdbcTypeHandle(
                            rsmd.getColumnType(i),
                            Optional.ofNullable(rsmd.getColumnTypeName(i)),
                            Optional.of(rsmd.getColumnDisplaySize(i)),
                            Optional.of(rsmd.getPrecision(i)),
                            Optional.empty(),
                            Optional.empty());
                    Optional<ColumnMapping> columnMapping = toColumnMapping(session, connection, typeHandle);
                    log.debug("Mapping data type of '%s' column '%s': %s mapped to %s", schemaTableName, columnName, typeHandle, columnMapping);
                    // skip unsupported column types
                    boolean nullable = (rsmd.isNullable(i) != columnNoNulls);
                    // Note: some databases (e.g. SQL Server) do not return column remarks/comment here.
                    getColumnMapping(columnMapping, columns, typeHandle, columnName, nullable, session);
                }
            }
            else {
                Map<String, Integer> vutf8ColumnsWithNoSize = resolveVutf8ColumnSizes(session, remoteTableName);
                resultSet = getColumnNames(remoteTableName, connection.getMetaData());
                while (resultSet.next()) {
                    allColumns++;
                    String columnName = resultSet.getString("COLUMN_NAME");

                    Optional<Integer> potentialVutf8ColumnSize = Optional.ofNullable(vutf8ColumnsWithNoSize.get(columnName));
                    Optional<Integer> columnSizeFromMetadata = Optional.ofNullable(resultSet.getInt("COLUMN_SIZE"));
                    Optional<Integer> columnSize = columnSizeFromMetadata;
                    if (potentialVutf8ColumnSize.isPresent()) {
                        columnSize = potentialVutf8ColumnSize;
                    }

                    JdbcTypeHandle typeHandle = new JdbcTypeHandle(
                            resultSet.getInt("DATA_TYPE"),
                            Optional.ofNullable(resultSet.getString("TYPE_NAME")),
                            columnSize,
                            Optional.of(resultSet.getInt("DECIMAL_DIGITS")),
                            Optional.empty(),
                            Optional.empty());
                    Optional<ColumnMapping> columnMapping = toColumnMapping(session, connection, typeHandle);
                    log.debug("Mapping data type of '%s' column '%s': %s mapped to %s", schemaTableName, columnName, typeHandle, columnMapping);
                    // skip unsupported column types
                    boolean nullable = (resultSet.getInt("NULLABLE") != columnNoNulls);
                    // Note: some databases (e.g. SQL Server) do not return column remarks/comment here.
                    getColumnMapping(columnMapping, columns, typeHandle, columnName, nullable, session);
                }
            }
            if (columns.isEmpty()) {
                // A table may have no supported columns. In rare cases (e.g. PostgreSQL) a table might have no columns at all.
                throw new TableNotFoundException(
                        schemaTableName,
                        format("Table '%s' has no supported columns (all %s columns are not supported)", schemaTableName, allColumns));
            }
            return ImmutableList.copyOf(columns);
        }
        catch (SQLException e) {
            throw new TrinoException(JDBC_ERROR, e);
        }
    }

    protected static ResultSet getColumnNames(RemoteTableName remoteTableName, DatabaseMetaData metadata)
            throws SQLException
    {
        return metadata.getColumns(
                remoteTableName.getCatalogName().orElse(null),
                null,
                remoteTableName.getTableName(),
                null);
    }

    private static void getColumnMapping(Optional<ColumnMapping> columnMapping, List<JdbcColumnHandle> columns, JdbcTypeHandle typeHandle, String columnName, boolean nullable, ConnectorSession session)
    {
        Optional<String> comment = Optional.empty();
        if (columnMapping.isPresent()) {
            columns.add(JdbcColumnHandle.builder()
                    .setColumnName(columnName)
                    .setJdbcTypeHandle(typeHandle)
                    .setColumnType(columnMapping.get().getType())
                    .setNullable(nullable)
                    .setComment(comment)
                    .build());
        }
        if (columnMapping.isEmpty()) {
            UnsupportedTypeHandling unsupportedTypeHandling = getUnsupportedTypeHandling(session);
            verify(unsupportedTypeHandling == IGNORE, "Unsupported type handling is set to %s, but toColumnMapping() returned empty", unsupportedTypeHandling);
        }
    }

    public Optional<ColumnMapping> toColumnMapping(ConnectorSession session, Connection connection, JdbcTypeHandle typeHandle)
    {
        // todo: data mapping for precision, decimals
        String jdbcTypeName = typeHandle.jdbcTypeName()
                .orElseThrow(() -> new TrinoException(JDBC_ERROR, "Type name is missing: " + typeHandle));
        Optional<ColumnMapping> mapping = getForcedMappingToVarchar(typeHandle);

        switch (jdbcTypeName.toLowerCase(Locale.ENGLISH)) {
            case "short":
                return Optional.of(smallintColumnMapping());

            case "int":
            case "integer":
                return Optional.of(integerColumnMapping());

            case "longlong":
                return Optional.of(bigintColumnMapping());

            case "u_short":
                return Optional.of(integerColumnMapping());

            case "u_int":
                return Optional.of(bigintColumnMapping());

            case "float":
            case "real":
                return Optional.of(realColumnMapping());

            case "double":
                return Optional.of(doubleColumnMapping());

            // These are queried as string datatypes as per comdb2 docs
            case "decimal32":
            case "decimal64":
            case "decimal128":
                return Optional.of(defaultVarcharColumnMapping(typeHandle.requiredColumnSize(), true));

            case "datetime":
            case "datetimeus":
                return Optional.of(timestampWithTimeZoneColumnMapping());

            case "blob":
            case "byte":
                return Optional.of(varbinaryColumnMapping());

            case "cstring":
            case "vutf8":
                return Optional.of(defaultVarcharColumnMapping(typeHandle.requiredColumnSize(), true));

            case "intervalds":
            case "intervaldsus":
                return Optional.of(intervalDayTimeColumnMapping());
            case "intervalym":
                return Optional.of(intervalYearMonthColumnMapping());
        }
        return mapping;
    }

    @Override
    public WriteMapping toWriteMapping(@SuppressWarnings("unused") ConnectorSession session, Type type)
    {
        if (type instanceof VarcharType) {
            VarcharType varcharType = (VarcharType) type;
            String dataType;
            if (varcharType.isUnbounded()) {
                dataType = "varchar";
            }
            else {
                dataType = "varchar(" + varcharType.getBoundedLength() + ")";
            }
            return WriteMapping.sliceMapping(dataType, varcharWriteFunction());
        }
        if (type instanceof CharType) {
            return WriteMapping.sliceMapping("char(" + ((CharType) type).getLength() + ")", charWriteFunction());
        }
        if (type instanceof DecimalType) {
            DecimalType decimalType = (DecimalType) type;
            String dataType = format("decimal(%s, %s)", decimalType.getPrecision(), decimalType.getScale());
            if (decimalType.isShort()) {
                return WriteMapping.longMapping(dataType, shortDecimalWriteFunction(decimalType));
            }
            return WriteMapping.objectMapping(dataType, longDecimalWriteFunction(decimalType));
        }

        throw new TrinoException(NOT_SUPPORTED, "Unsupported column type: " + type.getDisplayName());
    }

    private static ColumnMapping timestampWithTimeZoneColumnMapping()
    {
        return ColumnMapping.longMapping(
                TIMESTAMP_TZ_MILLIS,
                (resultSet, columnIndex) -> {
                    String timeStamp = resultSet.getString(columnIndex);
                    long millisUtc = resultSet.getTimestamp(columnIndex).getTime();
                    return packDateTimeWithZone(millisUtc, getTimeZoneKey(timeStamp));
                },
                timestampWithTimeZoneWriteFunction());
    }

    private static LongWriteFunction timestampWithTimeZoneWriteFunction()
    {
        return (statement, index, value) -> {
            long millisUtc = unpackMillisUtc(value);
            statement.setTimestamp(index, new Timestamp(millisUtc));
        };
    }

    private static String getTimeZoneKey(String timeStamp)
    {
        String timeZone = timeStamp.substring(timeStamp.indexOf(" ") + 1);
        TimeZone tz = TimeZone.getTimeZone(ZoneId.of(timeZone));
        return tz.getID();
    }

    private static ColumnMapping intervalDayTimeColumnMapping()
    {
        return ColumnMapping.longMapping(
                intervalDayTimeType,
                (resultSet, columnIndex) -> {
                    String stringInterval = resultSet.getString(columnIndex);
                    return IntervalDayTime.parseMillis(stringInterval);
                },
                intervalWriteFunction());
    }

    private static ColumnMapping intervalYearMonthColumnMapping()
    {
        return ColumnMapping.longMapping(
                intervalYearMonthType,
                (resultSet, columnIndex) -> {
                    String stringInterval = resultSet.getString(columnIndex);
                    return IntervalYearMonth.parseMonths(stringInterval);
                },
                intervalWriteFunction());
    }

    private static LongWriteFunction intervalWriteFunction()
    {
        return (statement, index, value) -> statement.setLong(index, value);
    }

    @Override
    public PreparedStatement buildSql(ConnectorSession session, Connection connection, JdbcSplit split, JdbcTableHandle tableHandle, List<JdbcColumnHandle> columnHandles)
            throws SQLException
    {
        return super.buildSql(session, connection, split, tableHandle, columnHandles);
    }

    public void setSystemTables(boolean sys)
    {
        isSystem = sys;
    }

    public boolean isLimitGuaranteed()
    {
        return false;
    }

    private static String singleQuote(String... objects)
    {
        return singleQuote(DOT_JOINER.join(objects));
    }

    private static String singleQuote(String literal)
    {
        return "\'" + literal + "\'";
    }

    @Override
    public Optional<JdbcExpression> implementAggregation(ConnectorSession session, AggregateFunction aggregate, Map<String, ColumnHandle> assignments)
    {
        // TODO support complex ConnectorExpressions
        return aggregateFunctionRewriter.rewrite(session, aggregate, assignments);
    }

    private static Optional<JdbcTypeHandle> toTypeHandle(DecimalType decimalType)
    {
        return Optional.of(new JdbcTypeHandle(Types.NUMERIC, Optional.of("decimal"), Optional.of(decimalType.getPrecision()), Optional.of(decimalType.getScale()), Optional.empty(), Optional.empty()));
    }

    @Override
    protected Optional<BiFunction<String, Long, String>> limitFunction()
    {
        return Optional.of((sql, limit) -> sql + " LIMIT " + limit);
    }

    @Override
    public boolean isLimitGuaranteed(ConnectorSession session)
    {
        return true;
    }
}

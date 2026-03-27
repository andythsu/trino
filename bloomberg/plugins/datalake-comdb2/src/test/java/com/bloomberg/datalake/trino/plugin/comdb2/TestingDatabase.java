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

import io.opentelemetry.api.OpenTelemetry;
import io.trino.plugin.base.mapping.DefaultIdentifierMapping;
import io.trino.plugin.jdbc.BaseJdbcConfig;
import io.trino.plugin.jdbc.DefaultQueryBuilder;
import io.trino.plugin.jdbc.DriverConnectionFactory;
import io.trino.plugin.jdbc.credential.CredentialProvider;
import io.trino.plugin.jdbc.credential.DefaultCredentialPropertiesProvider;
import io.trino.plugin.jdbc.logging.RemoteQueryModifier;
import io.trino.spi.security.ConnectorIdentity;
import io.trino.spi.type.TypeManager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Properties;

import static io.trino.type.InternalTypeManager.TESTING_TYPE_MANAGER;

final class TestingDatabase
        implements AutoCloseable
{
    private final Connection connection;
    private final Comdb2Client comdb2Client;
    private static final TypeManager TYPE_MANAGER = TESTING_TYPE_MANAGER;

    public TestingDatabase()
            throws SQLException
    {
        //todo: find a test database
        String connectionUrl = "jdbc:comdb2://dev-10-178-5-252.ob1.bcc.bloomberg.com:8080/example";
        comdb2Client = new Comdb2Client(
                new BaseJdbcConfig(),
                new DriverConnectionFactory(new com.bloomberg.comdb2.jdbc.Driver(), connectionUrl, new Properties(), new DefaultCredentialPropertiesProvider(new CredentialProvider() {
                    @Override
                    public Optional<String> getConnectionUser(Optional<ConnectorIdentity> connectorIdentity)
                    {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<String> getConnectionPassword(Optional<ConnectorIdentity> connectorIdentity)
                    {
                        return Optional.empty();
                    }
                }), OpenTelemetry.noop()),
                new Comdb2Config(),
                new DefaultQueryBuilder(RemoteQueryModifier.NONE),
                TYPE_MANAGER,
                new DefaultIdentifierMapping(),
                RemoteQueryModifier.NONE);
        connection = DriverManager.getConnection(connectionUrl);
        //tester
        connection.createStatement().execute("SELECT * FROM comdb2_type_samples");
        connection.commit();
    }

    @Override
    public void close()
            throws SQLException
    {
        connection.close();
    }

    public Connection getConnection()
    {
        return connection;
    }

    public Comdb2Client getComdb2Client()
    {
        return comdb2Client;
    }
}

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

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.opentelemetry.api.OpenTelemetry;
import io.trino.plugin.base.mapping.DefaultIdentifierMapping;
import io.trino.plugin.jdbc.BaseJdbcConfig;
import io.trino.plugin.jdbc.ConnectionFactory;
import io.trino.plugin.jdbc.DefaultQueryBuilder;
import io.trino.plugin.jdbc.DriverConnectionFactory;
import io.trino.plugin.jdbc.ForBaseJdbc;
import io.trino.plugin.jdbc.credential.CredentialProvider;
import io.trino.plugin.jdbc.credential.DefaultCredentialPropertiesProvider;
import io.trino.plugin.jdbc.logging.RemoteQueryModifier;
import io.trino.spi.type.TypeManager;
import org.h2.Driver;

import java.util.Properties;

import static io.trino.type.InternalTypeManager.TESTING_TYPE_MANAGER;

public class TestingComdb2Module
        implements Module
{
    private static final TypeManager TYPE_MANAGER = TESTING_TYPE_MANAGER;

    @Override
    public void configure(Binder binder) {}

    @Provides
    @ForBaseJdbc
    public Comdb2Client provideComdb2Client(Comdb2Config config, ConnectionFactory connectionFactory)
    {
        return new Comdb2Client(new BaseJdbcConfig(),
                                connectionFactory,
                                config,
                                new DefaultQueryBuilder(RemoteQueryModifier.NONE),
                                TYPE_MANAGER,
                                new DefaultIdentifierMapping(),
                                RemoteQueryModifier.NONE);
    }

    @Provides
    @Singleton
    @ForBaseJdbc
    public ConnectionFactory getConnectionFactory(BaseJdbcConfig config, CredentialProvider credentialProvider)
    {
        return new DriverConnectionFactory(new Driver(), config.getConnectionUrl(), new Properties(), new DefaultCredentialPropertiesProvider(credentialProvider), OpenTelemetry.noop());
    }
}

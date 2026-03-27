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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.units.Duration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class TestComdb2Config
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(Comdb2Config.class)
                .setConnectionUrl(null)
                .setCaseInsensitiveNameMatching(false)
                .setCaseInsensitiveNameMatchingCacheTtl(new Duration(1, MINUTES))
                .setJdbcTypesMappedToVarchar("")
                .setMetadataCacheTtl(Duration.valueOf("0m"))
                .setCacheMissing(false)
                .setDriverClass(null)
                .setIncludeSystemTables(false)
                .setResolveVutf8ColumnSizes(false));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("connection-url", "jdbc:comdb2://dev/mikedb?default_type=dev&room=ORG&comdb2dbname=comdb3db&user=none&password=none")
                .put("case-insensitive-name-matching", "true")
                .put("case-insensitive-name-matching.cache-ttl", "1s")
                .put("jdbc-types-mapped-to-varchar", "mytype,struct_type1")
                .put("metadata.cache-ttl", "1s")
                .put("metadata.cache-missing", "true")
                .put("driver-class", "com.bloomberg.system.comdb2.jdbc.Driver")
                .put("include-system-tables", "true")
                .put("resolve-vutf8-column-sizes", "true")
                .buildOrThrow();

        Comdb2Config expected = new Comdb2Config()
                .setConnectionUrl("jdbc:comdb2://dev/mikedb?default_type=dev&room=ORG&comdb2dbname=comdb3db&user=none&password=none")
                .setCaseInsensitiveNameMatching(true)
                .setCaseInsensitiveNameMatchingCacheTtl(new Duration(1, SECONDS))
                .setJdbcTypesMappedToVarchar("mytype, struct_type1")
                .setMetadataCacheTtl(Duration.valueOf("1s"))
                .setCacheMissing(true)
                .setDriverClass("com.bloomberg.system.comdb2.jdbc.Driver")
                .setIncludeSystemTables(true)
                .setResolveVutf8ColumnSizes(true);

        assertFullMapping(properties, expected);

        assertThat(expected.getJdbcTypesMappedToVarchar()).isEqualTo(ImmutableSet.of("mytype", "struct_type1"));
    }
}

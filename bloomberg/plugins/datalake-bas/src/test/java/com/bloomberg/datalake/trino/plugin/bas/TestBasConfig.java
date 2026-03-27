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

import io.airlift.units.Duration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;

class TestBasConfig
{
    @Test
    void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(BasConfig.class)
                .setGenericUUID(0)
                .setHost("localhost")
                .setMetadataFile(null)
                .setUuidGroupPattern(null)
                .setRetryMaxAttempts(3)
                .setRetryBackoff(Duration.valueOf("50ms"))
                .setBlockTimeout(Duration.valueOf("1m")));
    }

    @Test
    void testExplicitPropertyMappings()
    {
        Map<String, String> properties = Map.of(
                "metadata", "/var/trino/data/metadata.json",
                "host", "bas-web-dev.bdns.bloomberg.com",
                "generic-uuid", "6834118",
                "retry-max-attempts", "10",
                "retry-backoff", "100ms",
                "block-timeout", "5m",
                "uuid-group-pattern", "(.*)");

        BasConfig expected = new BasConfig()
                .setMetadataFile("/var/trino/data/metadata.json")
                .setHost("bas-web-dev.bdns.bloomberg.com")
                .setGenericUUID(6834118)
                .setRetryMaxAttempts(10)
                .setRetryBackoff(Duration.valueOf("100ms"))
                .setBlockTimeout(Duration.valueOf("5m"))
                .setUuidGroupPattern("(.*)");

        assertFullMapping(properties, expected);
    }
}

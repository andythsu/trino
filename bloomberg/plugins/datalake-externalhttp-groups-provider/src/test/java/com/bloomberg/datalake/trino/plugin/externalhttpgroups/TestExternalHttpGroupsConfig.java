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
package com.bloomberg.datalake.trino.plugin.externalhttpgroups;

import io.airlift.units.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
public class TestExternalHttpGroupsConfig
{
    @Test
    public void testExplicitPropertyMappings()
            throws Exception
    {
        Map<String, String> properties = Map.of(
                "externalhttpgroups.uri", "https://testurl.com",
                "externalhttpgroups.cache-ttl", "10m");

        ExternalHttpGroupsConfig expected = new ExternalHttpGroupsConfig()
                .setConfigUri(new URI("https://testurl.com"))
                .setCacheTTL(Duration.succinctDuration(10, TimeUnit.MINUTES));

        assertFullMapping(properties, expected);
    }
}

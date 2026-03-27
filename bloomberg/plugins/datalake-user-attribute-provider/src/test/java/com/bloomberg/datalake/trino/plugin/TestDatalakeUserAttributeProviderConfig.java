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
package com.bloomberg.datalake.trino.plugin;

import com.google.common.collect.ImmutableMap;
import com.google.common.net.HostAndPort;
import io.airlift.units.Duration;
import jakarta.validation.constraints.AssertTrue;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;
import static io.airlift.testing.ValidationAssertions.assertFailsValidation;
import static io.airlift.testing.ValidationAssertions.assertValidates;

public class TestDatalakeUserAttributeProviderConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(DatalakeUserAttributeProviderConfig.class)
                .setBasHost(null)
                .setSesBasEnabled(true)
                .setBlpSessionAttributesNode(null)
                .setSesgetRetryBackoff(Duration.valueOf("10ms"))
                .setSesgetRetries(3)
                .setHttpUri(null)
                .setHttpCacheTtl(Duration.valueOf("10m")));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        assertFullMapping(
                ImmutableMap.<String, String>builder()
                        .put("bas.host", "bashub.dev.bloomberg.com")
                        .put("ses-bas.enabled", "false")
                        .put("bas.blp-session-attributes-node", "blp_session")
                        .put("bas.sesget.retry-backoff", "42s")
                        .put("bas.sesget.retries", "42")
                        .put("http.uri", "https://test.example.com/api")
                        .put("http.cache-ttl", "5m")
                        .buildOrThrow(),
                new DatalakeUserAttributeProviderConfig()
                        .setBasHost(HostAndPort.fromHost("bashub.dev.bloomberg.com"))
                        .setSesBasEnabled(false)
                        .setBlpSessionAttributesNode("blp_session")
                        .setSesgetRetryBackoff(Duration.valueOf("42s"))
                        .setSesgetRetries(42)
                        .setHttpUri(URI.create("https://test.example.com/api"))
                        .setHttpCacheTtl(Duration.valueOf("5m")));
    }

    @Test
    public void testZeroCacheTtlDisablesCaching()
    {
        assertValidates(new DatalakeUserAttributeProviderConfig()
                .setHttpCacheTtl(Duration.valueOf("0s")));
    }

    @Test
    public void testHttpUriRequiredWhenSesBasDisabled()
    {
        assertFailsValidation(
                new DatalakeUserAttributeProviderConfig()
                        .setSesBasEnabled(false),
                "httpUriConfigValid",
                "http.uri must be configured when ses-bas.enabled is false",
                AssertTrue.class);
    }

    @Test
    public void testHttpUriNotRequiredWhenSesBasEnabled()
    {
        assertValidates(new DatalakeUserAttributeProviderConfig()
                .setSesBasEnabled(true));
    }
}

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

import com.bloomberg.bas.codecfactory.CodecFactory;
import com.bloomberg.basreactor.bas.reactor.client.BasClient;
import com.bloomberg.basreactor.external.basmessage.types.ServiceInformation;
import com.bloomberg.basreactor.external.schema.SchemaUtilities;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.hubspot.jinjava.Jinjava;
import io.trino.spi.TrinoException;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestBasServiceClient
{
    private final String basWebDev = "bas-web-dev.bdns.bloomberg.com";
    private final long genericUUID = 6834118;

    private final String bxsdsvcServiceInfo = "bxsdsvc:171365-1.4";
    private final BasClient bxsdsvcClient;

    private final long retryMaxAttempts = 2;
    private final Duration retryBackoff = Duration.ofMillis(50);

    TestBasServiceClient()
    {
        // Dummy client to set BAS_HOST in SchemaUtilities.Bxsdsvc to basWebDev
        BasClient dummyClient = BasClient.builder().host(basWebDev).build();
        SchemaUtilities schemaUtilities = SchemaUtilities.create(dummyClient);
        InputStream schemaInputStream = schemaUtilities.fetchInputStream(ObjectNode.class, bxsdsvcServiceInfo);

        CodecFactory codecFactory = CodecFactory.newInstance(schemaInputStream);
        bxsdsvcClient = BasClient
                .builder()
                .serviceInformation(ServiceInformation.parse(bxsdsvcServiceInfo))
                .host(basWebDev)
                .codecFactory(codecFactory)
                .build();
    }

    @Test
    void testNoParameters()
    {
        Map<String, String> functionTemplates = ImmutableMap.of(
                "test-func",
                "{\"getId\":{\"serviceName\":\"tadatasv\"}}");
        BasServiceClient basServiceClient = new BasServiceClient(
                bxsdsvcClient,
                new Jinjava(),
                functionTemplates,
                retryMaxAttempts,
                retryBackoff);
        Map<String, Object> response = basServiceClient.execute("test-func", ImmutableMap.of(), genericUUID).blockFirst();
        assertThat(((Map<String, Object>) response.get("getId")).get("success")).isEqualTo(44663);
    }

    @Test
    void testWithParameters()
    {
        Map<String, String> functionTemplates = ImmutableMap.of(
                "test-func",
                "{\"getId\":{\"serviceName\":\"{{ serviceName }}\"}}");
        BasServiceClient basServiceClient = new BasServiceClient(
                bxsdsvcClient,
                new Jinjava(),
                functionTemplates,
                retryMaxAttempts,
                retryBackoff);
        Map<String, Object> response = basServiceClient.execute("test-func", ImmutableMap.of("serviceName", "tadatasv"), genericUUID).blockFirst();
        assertThat(((Map<String, Object>) response.get("getId")).get("success")).isEqualTo(44663);
    }

    @Test
    void testBadRequest()
    {
        Map<String, String> functionTemplates = ImmutableMap.of(
                "test-func",
                "random-string-non-json");
        BasServiceClient basServiceClient = new BasServiceClient(
                bxsdsvcClient,
                new Jinjava(),
                functionTemplates,
                retryMaxAttempts,
                retryBackoff);
        assertThatThrownBy(() -> basServiceClient.execute("test-func", ImmutableMap.of(), genericUUID).blockFirst())
                .isInstanceOf(TrinoException.class);
    }
}

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

import com.bloomberg.codegen.sesget.BlockSchema;
import com.bloomberg.codegen.sesget.Response;
import com.bloomberg.codegen.sesget.SessionBlock;
import com.bloomberg.codegen.sesget.SessionHeader;
import com.bloomberg.codegen.sesget.SessionResponse;
import com.bloomberg.datalake.bpi.DatalakeBSSOBloombergPrincipal;
import com.bloomberg.testcontainers.bas.BasRouterExtension;
import com.google.common.collect.ImmutableMap;
import io.trino.spi.security.UserAttributeProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.InetSocketAddress;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(BasRouterExtension.class)
public class TestDatalakeUserAttributeProvider
{
    private final TestingSesgetBasService testingSesgetBasService;
    private final UserAttributeProvider userAttributeProvider;

    public TestDatalakeUserAttributeProvider(@BasRouterExtension.Dynamic InetSocketAddress serviceAddress, @BasRouterExtension.Client InetSocketAddress clientAddress)
    {
        this.testingSesgetBasService = new TestingSesgetBasService(serviceAddress);
        this.userAttributeProvider = new DatalakeUserAttributeProviderFactory().create(ImmutableMap.of("bas.host", format("%s:%d", clientAddress.getHostString(),
                clientAddress.getPort())));
    }

    @Test
    public void testSesgetSuccessfulFlow()
    {
        String sessionId = UUID.randomUUID().toString();
        AtomicInteger requestCounter = new AtomicInteger(0);
        SessionHeader sessionHeader = new SessionHeader()
                .withAvailableBlocks("CUSTOMER", "IDENTITY")
                .withCreated(OffsetDateTime.now().minusMinutes(1))
                .withExpires(OffsetDateTime.now().plusMinutes(1))
                .withSessionId(sessionId)
                .withSessionType(4);

        testingSesgetBasService.setRequestHandler(request -> {
            assertThat(request.getRetrieveSession().getSessionId()).isEqualTo(sessionId);

            if (requestCounter.getAndIncrement() == 0) {
                assertThat(request.getRetrieveSession().getBlocks()).isEmpty();
                return new Response().withSession(new SessionResponse().withHeader(sessionHeader));
            }

            assertThat(request.getRetrieveSession().getBlocks()).containsExactly("CUSTOMER", "IDENTITY");
            return new Response().withSession(new SessionResponse()
                    .withHeader(sessionHeader)
                    .withBlocks(
                            new SessionBlock()
                                    .withName("CUSTOMER")
                                    .withData("""
                                            {
                                                "customerNumber": 123,
                                                "industry": "BA",
                                                "countryCode": "ENG",
                                                "salesRegion": "LO"
                                            }
                                            """)
                                    .withDataEncoding("JSON")
                                    .withSchema(new BlockSchema().withName("customer").withVersion(1)),
                            new SessionBlock()
                                    .withName("IDENTITY")
                                    .withData("""
                                             {
                                                 "uuid": 123,
                                                 "userNumber": 456,
                                                 "customerNumber": 678,
                                                 "firmNumber": 9001,
                                                 "username": "TEST_USER",
                                                 "isClient": false,
                                                 "impersonationType": null,
                                                 "hasBbaSubscription": true,
                                                 "loginType": 0
                                             }
                                            """)
                                    .withSchema(new BlockSchema().withName("identity").withVersion(5))));
        });

        Map<String, Object> userAttributes = userAttributeProvider.getUserAttributes("test_user",
                Optional.of(new DatalakeBSSOBloombergPrincipal("test_user", Optional.of(123L), Optional.of(sessionId))));

        // Building a HashMap as Map.of() and ImmutableMap.of() don't allow null values
        Map<String, Object> identityValues = new HashMap<>(ImmutableMap.of(
                "uuid", 123,
                "userNumber", 456,
                "customerNumber", 678,
                "firmNumber", 9001,
                "username", "TEST_USER",
                "isClient", false,
                "hasBbaSubscription", true,
                "loginType", 0));
        identityValues.put("impersonationType", null);

        assertThat(userAttributes).isNotNull();
        assertThat(userAttributes).isEqualTo(ImmutableMap.of(
                "CUSTOMER", ImmutableMap.of(
                        "customerNumber", 123,
                        "industry", "BA",
                        "countryCode", "ENG",
                        "salesRegion", "LO"),
                "IDENTITY", identityValues));

        assertThat(requestCounter.get()).isEqualTo(2);
    }
}

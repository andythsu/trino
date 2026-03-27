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
import com.bloomberg.basreactor.external.schema.SchemaUtilities;
import com.bloomberg.datalake.trino.plugin.bas.config.BasConfigClient;
import com.bloomberg.datalake.trino.plugin.bas.config.BasFunctionConfiguration;
import com.bloomberg.datalake.trino.plugin.bas.config.BasServiceConfiguration;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.hubspot.jinjava.Jinjava;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class BasServiceClientProvider
{
    private final Map<String, BasServiceClient> clientMap;

    @Inject
    public BasServiceClientProvider(BasConfigClient configClient, BasConfig config, Jinjava jinja)
    {
        requireNonNull(configClient, "configClient is null");
        requireNonNull(config, "config is null");
        requireNonNull(jinja, "jinja is null");

        long retryMaxAttempts = config.getRetryMaxAttempts();
        Duration retryBackoff = Duration.ofMillis(config.getRetryBackoff().toMillis());

        ImmutableMap.Builder<String, BasServiceClient> builder = ImmutableMap.builder();
        for (BasServiceConfiguration service : configClient.getServiceConfigs()) {
            Map<String, String> functionTemplates = service.getFunctions().stream()
                    .collect(Collectors.toUnmodifiableMap(BasFunctionConfiguration::getName, BasFunctionConfiguration::getRequestTemplate));

            // We use SchemaUtilities which internally calls bxsdsvc to fetch BAS schemas.
            // The following makes sure the calls to bxsdsvc use the BAS_HOST from the config.
            BasClient bxsdsvcClient = BasClient.builder().host(config.getHost()).build();
            SchemaUtilities schemaUtilities = SchemaUtilities.create(bxsdsvcClient);

            // Fetch schema from bxsdsvc and create a codecfactory  for the client. The codecfactory contains the codec
            // for encoding BER requests and decoding BER responses for this service.
            String serviceInfo = service.getServiceInfo().toString();
            InputStream schemaInputStream = schemaUtilities.fetchInputStream(getClass(), serviceInfo);
            CodecFactory codecFactory = CodecFactory.newInstance(schemaInputStream);

            BasClient basClient = BasClient
                    .builder()
                    .serviceInformation(service.getServiceInfo())
                    .host(config.getHost())
                    .codecFactory(codecFactory)
                    .build();

            BasServiceClient client = new BasServiceClient(
                    basClient,
                    jinja,
                    functionTemplates,
                    retryMaxAttempts,
                    retryBackoff);
            builder.put(service.getName(), client);
        }
        clientMap = builder.buildOrThrow();
    }

    public BasServiceClient getClient(String serviceName)
    {
        return clientMap.get(serviceName);
    }
}

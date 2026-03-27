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
package com.bloomberg.datalake.trino.plugin.bas.integration;

import com.bloomberg.bas.codecfactory.CodecFactory;
import com.bloomberg.basreactor.bas.reactor.client.BasClient;
import com.bloomberg.basreactor.bas.reactor.server.BasServer;
import com.bloomberg.basreactor.bas.reactor.server.DisposableServer;
import com.bloomberg.basreactor.external.schema.SchemaUtilities;
import reactor.core.publisher.Flux;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

class TestingBasService<REQUEST, RESPONSE>
        implements AutoCloseable
{
    private final DisposableServer basServer;
    private final List<REQUEST> requests = new ArrayList<>();
    private Function<REQUEST, RESPONSE> requestHandler;

    TestingBasService(String serviceInfo, Class<REQUEST> requestClazz, Class<RESPONSE> responseClazz, InetSocketAddress basRouterAddress)
    {
        this(SchemaUtilities.create(BasClient.create()).fetchInputStream(requestClazz, serviceInfo), serviceInfo, requestClazz, responseClazz, basRouterAddress);
    }

    TestingBasService(InputStream schema, String serviceInfo, Class<REQUEST> requestClazz, Class<RESPONSE> responseClazz, InetSocketAddress basRouterAddress)
    {
        CodecFactory codecFactory = CodecFactory.newInstance(schema);
        basServer = BasServer.builder()
                .address(basRouterAddress)
                .service()
                .serviceInformation(serviceInfo)
                .instance(1)
                .serviceSupplier(() -> new com.bloomberg.basreactor.bas.reactor.server.BasService<REQUEST, RESPONSE>()
                {
                    @Override
                    public CodecFactory getCodecFactory()
                    {
                        return codecFactory;
                    }

                    @Override
                    public Class<REQUEST> getRequestType()
                    {
                        return requestClazz;
                    }

                    @Override
                    public Class<RESPONSE> getResponseType()
                    {
                        return responseClazz;
                    }

                    @Override
                    public Flux<RESPONSE> process(REQUEST request)
                    {
                        requests.add(request);
                        return Flux.just(requireNonNull(requestHandler, "requestHandler is null").apply(request));
                    }
                })
                .add()
                .build()
                .bindNow();
    }

    void setRequestHandler(Function<REQUEST, RESPONSE> requestHandler)
    {
        this.requestHandler = requireNonNull(requestHandler, "requestHandler is null");
    }

    List<REQUEST> getRequests()
    {
        return requests;
    }

    void clear()
    {
        requests.clear();
        requestHandler = null;
    }

    @Override
    public void close()
    {
        basServer.dispose();
    }
}

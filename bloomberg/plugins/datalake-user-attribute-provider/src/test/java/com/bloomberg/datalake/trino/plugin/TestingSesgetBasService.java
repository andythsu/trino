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

import com.bloomberg.basreactor.annotations.BasService;
import com.bloomberg.basreactor.annotations.BasServiceProxy;
import com.bloomberg.basreactor.annotations.RequestHandler;
import com.bloomberg.basreactor.bas.reactor.server.BasServer;
import com.bloomberg.basreactor.bas.reactor.server.DisposableServer;
import com.bloomberg.codegen.sesget.Request;
import com.bloomberg.codegen.sesget.Response;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public class TestingSesgetBasService
        implements AutoCloseable
{
    private DisposableServer basServer;
    private Function<Request, Response> requestHandler;

    public TestingSesgetBasService(InetSocketAddress basRouterAddress)
    {
        basServer = BasServer.builder()
                .addServiceSpec(BasServiceProxy.create(new Service()))
                .address(basRouterAddress)
                .build()
                .bindNow();
    }

    public void setRequestHandler(Function<Request, Response> requestHandler)
    {
        this.requestHandler = requireNonNull(requestHandler, "requestHandler is null");
    }

    @Override
    public void close()
    {
        basServer.dispose();
    }

    @BasService(schema = "classpath:/sesget-1.1.xml")
    private class Service
    {
        @RequestHandler
        public Mono<Response> handle(Request request)
        {
            return Mono.just(requireNonNull(requestHandler, "requestHandler is null").apply(request));
        }
    }
}

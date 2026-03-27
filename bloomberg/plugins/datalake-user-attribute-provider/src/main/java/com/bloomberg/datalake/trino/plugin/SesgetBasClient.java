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

import com.bloomberg.basreactor.bas.reactor.client.BasClient;
import com.bloomberg.basreactor.bas.reactor.client.exception.BasClientErrorInfoException;
import com.bloomberg.basreactor.bas.reactor.client.exception.BasClientRuntimeException;
import com.bloomberg.basreactor.external.schema.SchemaUtilities;
import com.bloomberg.codegen.sesget.Request;
import com.bloomberg.codegen.sesget.Response;
import com.bloomberg.codegen.sesget.RetrieveSessionRequest;
import com.bloomberg.codegen.sesget.SessionResponse;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import reactor.core.publisher.Mono;
import reactor.util.retry.RetryBackoffSpec;
import reactor.util.retry.RetrySpec;

import static java.lang.String.format;

public class SesgetBasClient
{
    private static final Logger log = Logger.get(SesgetBasClient.class);

    private final BasClient basClient;
    private final RetryBackoffSpec retrySpec;

    @Inject
    public SesgetBasClient(DatalakeUserAttributeProviderConfig datalakeUserAttributeProviderConfig)
    {
        BasClient.Builder<?> builder = SchemaUtilities.getInstance().builder("classpath:/sesget-1.1.xml", Request.class, Response.class);
        datalakeUserAttributeProviderConfig.getBasHost().ifPresent(basHost -> {
            builder.host(basHost.getHost());
            if (basHost.hasPort()) {
                builder.port(basHost.getPort());
            }
        });
        this.basClient = builder.build();
        this.retrySpec = RetrySpec.backoff(datalakeUserAttributeProviderConfig.getSesgetRetries(), datalakeUserAttributeProviderConfig.getSesgetRetryBackoff().toJavaTime());
    }

    /**
     * <p>The sesget protocol is two-pass:</p>
     * <ol>
     *   <li>The initial request contains just the <code>sessionId</code>. The response contains the <code>availableBlocks</code> but not the content for the blocks.</li>
     *   <li>The second request contains the <code>sessionId</code> and the <code>availableBlocks</code> form the response. The Response has the block contents.</li>
     * </ol>
     *
     * @param sessionId Sessions Id
     * @return A {@link SessionResponse} object containing blocks with data if the requests are executed successfully OR an empty Optional if the first request returned
     * no available blocks or there was an error.
     */
    public SessionResponse retrieveSession(String sessionId)
    {
        return callRetrieveSession(new RetrieveSessionRequest().withSessionId(sessionId)).flatMap(sessionResponse -> {
            if (sessionResponse.getHeader().getAvailableBlocks().isEmpty()) {
                return Mono.error(new IllegalStateException("Response from sesget returned no available blocks"));
            }
            return callRetrieveSession(new RetrieveSessionRequest().withSessionId(sessionId).withBlocks(sessionResponse.getHeader().getAvailableBlocks()));
        }).block();
    }

    private Mono<SessionResponse> callRetrieveSession(RetrieveSessionRequest request)
    {
        return basClient.request(new Request().withRetrieveSession(request)).retrieve(Response.class).mono().flatMap(response -> {
            if (response.getSessionError() != null) {
                return Mono.error(new BasClientRuntimeException("sesget returned error with reason: " + response.getSessionError().getReason()));
            }
            return Mono.just(response.getSession());
        }).retryWhen(retrySpec.filter(t -> t instanceof BasClientRuntimeException).onRetryExhaustedThrow((_, retrySignal) -> {
            Throwable cause = retrySignal.failure();
            if (cause instanceof BasClientErrorInfoException basClientErrorInfoException) {
                return new BasClientRuntimeException(format("sesget returned error after %d retries: %s", retrySignal.totalRetries(), toErrorString(basClientErrorInfoException)), cause);
            }
            return cause;
        }));
    }

    private static String toErrorString(BasClientErrorInfoException e)
    {
        if (e.getErrorInfo().getErrorCode() != null) {
            return format("Error code %d/%d with flags %d - %s", e.getErrorInfo().getErrorCode().getCategory(), e.getErrorInfo().getErrorCode().getValue(),
                    e.getErrorInfo().getInternalBasFlags(), e.getErrorInfo().getDescription());
        }
        else {
            return e.getErrorInfo().getDescription();
        }
    }
}

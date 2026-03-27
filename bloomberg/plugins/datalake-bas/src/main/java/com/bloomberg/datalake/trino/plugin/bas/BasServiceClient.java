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

import com.bloomberg.basreactor.bas.reactor.client.BasClient;
import com.bloomberg.basreactor.bas.reactor.client.exception.BasClientErrorInfoException;
import com.bloomberg.basreactor.external.basmessage.impl.UserIdentificationImpl;
import com.bloomberg.basreactor.external.basmessage.types.EncodingType;
import com.bloomberg.basreactor.external.basmessage.types.useridentification.Attribute;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.interpret.RenderResult;
import io.airlift.log.Logger;
import io.trino.spi.TrinoException;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.bloomberg.datalake.trino.plugin.bas.BasErrorCode.BAS_CLIENT_ERROR;
import static com.bloomberg.datalake.trino.plugin.bas.BasErrorCode.BAS_TEMPLATING_ERROR;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class BasServiceClient
{
    private static final Logger log = Logger.get(BasServiceClient.class);

    private final BasClient basClient;
    private final Jinjava jinja;
    private final Map<String, String> functionTemplates;

    private final long retryMaxAttempts;
    private final Duration retryBackoff;

    public BasServiceClient(BasClient basClient, Jinjava jinja, Map<String, String> functionTemplates, long retryMaxAttempts, Duration retryBackoff)
    {
        requireNonNull(basClient, "service information is null");
        requireNonNull(functionTemplates, "function templates is null");
        requireNonNull(retryBackoff, "retry backoff duration is null");
        requireNonNull(jinja, "jinja is null");

        this.basClient = basClient;
        this.functionTemplates = functionTemplates;
        this.retryMaxAttempts = retryMaxAttempts;
        this.retryBackoff = retryBackoff;
        this.jinja = jinja;
    }

    public Flux<Map<String, Object>> execute(String functionName, Map<String, Object> parameters, Long uuid)
    {
        String template = functionTemplates.get(functionName);

        log.debug("Rendering template for function %s with parameters %s", functionName, parameters);

        RenderResult jinjaResult = jinja.renderForResult(template, parameters);

        if (!jinjaResult.getErrors().isEmpty()) {
            throw new TrinoException(BAS_TEMPLATING_ERROR, format("Errors rendering template: %s",
                    jinjaResult.getErrors().stream().map(Objects::toString).collect(Collectors.joining("; "))));
        }

        ObjectNode request;
        try {
            request = new ObjectMapper().readValue(jinjaResult.getOutput().getBytes(StandardCharsets.UTF_8), ObjectNode.class);
        }
        catch (IOException exception) {
            throw new TrinoException(BAS_TEMPLATING_ERROR, "Error decoding jinjaResult to ObjectNode", exception);
        }

        if (log.isDebugEnabled()) {
            log.debug("Sending request to function %s: [ %s ]", functionName, request);
        }

        UserIdentificationImpl userIndent = new UserIdentificationImpl();
        userIndent.set(Attribute.UUID, uuid);

        return basClient
                .request()
                .userIdentification(userIndent)
                .encoding(EncodingType.BER)
                .body(request)
                .retrieve((Class<Map<String, Object>>) (Class) Map.class)
                .flux()
                .retryWhen(Retry.backoff(retryMaxAttempts, retryBackoff)
                        .filter(t -> t instanceof BasClientErrorInfoException)
                        .onRetryExhaustedThrow((rbs, rs) -> {
                            Throwable t = rs.failure();
                            if (t instanceof BasClientErrorInfoException) {
                                return new TrinoException(
                                        BAS_CLIENT_ERROR,
                                        format("BAS request failed after %s retries: %s", rs.totalRetries(), toErrorString((BasClientErrorInfoException) t)),
                                        rs.failure());
                            }
                            return new TrinoException(BAS_CLIENT_ERROR, format("BAS request failed after %s retries: %s", rs.totalRetries(), t), t);
                        }));
    }

    private static String toErrorString(BasClientErrorInfoException e)
    {
        if (e.getErrorInfo().getErrorCode() != null) {
            return format("Error code %d/%d with flags %d - %s",
                    e.getErrorInfo().getErrorCode().getCategory(),
                    e.getErrorInfo().getErrorCode().getValue(),
                    e.getErrorInfo().getInternalBasFlags(),
                    e.getErrorInfo().getDescription());
        }
        else {
            return e.getErrorInfo().getDescription();
        }
    }
}

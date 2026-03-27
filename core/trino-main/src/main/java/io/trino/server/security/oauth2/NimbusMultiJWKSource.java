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
package io.trino.server.security.oauth2;

import com.google.common.collect.ImmutableList;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import java.util.List;

import static java.util.Objects.requireNonNull;

public class NimbusMultiJWKSource
        <C extends SecurityContext> implements JWKSource<C>
{
    private final List<JWKSource<C>> jwkSources;

    public NimbusMultiJWKSource(List<JWKSource<C>> jwkSources)
    {
        this.jwkSources = requireNonNull(jwkSources, "jwkSources is null");
    }

    @Override
    public List<JWK> get(JWKSelector jwkSelector, final C context)
            throws KeySourceException
    {
        ImmutableList.Builder<JWK> jwks = ImmutableList.builder();
        for (var jwkSource : jwkSources) {
            jwks.addAll(jwkSource.get(jwkSelector, context));
        }
        return jwks.build();
    }
}

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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestNimbusMultiJWKSource
{
    private static JWKSource<SecurityContext> healthySource(JWK key)
    {
        return new ImmutableJWKSet<>(new JWKSet(key));
    }

    private static JWKSource<SecurityContext> failingSource(String message)
    {
        return (selector, context) -> { throw new KeySourceException(message); };
    }

    @Test
    public void testAllSourcesHealthy()
            throws JOSEException
    {
        RSAKey key1 = new RSAKeyGenerator(2048).generate();
        RSAKey key2 = new RSAKeyGenerator(2048).generate();
        NimbusMultiJWKSource<SecurityContext> source = new NimbusMultiJWKSource<>(List.of(
                healthySource(key1),
                healthySource(key2)));

        List<JWK> result = source.get(new JWKSelector(new com.nimbusds.jose.jwk.JWKMatcher.Builder().build()), null);
        assertThat(result).hasSize(2);
    }

    @Test
    public void testOneSourceFailsContinuesToOthers()
            throws JOSEException
    {
        RSAKey key = new RSAKeyGenerator(2048).generate();
        NimbusMultiJWKSource<SecurityContext> source = new NimbusMultiJWKSource<>(List.of(
                failingSource("timeout"),
                healthySource(key)));

        List<JWK> result = source.get(new JWKSelector(new com.nimbusds.jose.jwk.JWKMatcher.Builder().build()), null);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getKeyID()).isEqualTo(key.getKeyID());
    }

    @Test
    public void testAllSourcesFailThrows()
    {
        NimbusMultiJWKSource<SecurityContext> source = new NimbusMultiJWKSource<>(List.of(
                failingSource("timeout on source 1"),
                failingSource("timeout on source 2")));

        assertThatThrownBy(() -> source.get(new JWKSelector(new com.nimbusds.jose.jwk.JWKMatcher.Builder().build()), null))
                .isInstanceOf(KeySourceException.class)
                .hasMessage("All JWK sources failed")
                .hasSuppressedException(new KeySourceException("timeout on source 1"))
                .hasSuppressedException(new KeySourceException("timeout on source 2"));
    }

    @Test
    public void testFirstSourceFailsSecondHealthy()
            throws JOSEException
    {
        RSAKey key = new RSAKeyGenerator(2048).generate();
        NimbusMultiJWKSource<SecurityContext> source = new NimbusMultiJWKSource<>(List.of(
                failingSource("JWKS endpoint unavailable"),
                healthySource(key)));

        // should not throw — one healthy source is sufficient
        List<JWK> result = source.get(new JWKSelector(new com.nimbusds.jose.jwk.JWKMatcher.Builder().build()), null);
        assertThat(result).hasSize(1);
    }
}

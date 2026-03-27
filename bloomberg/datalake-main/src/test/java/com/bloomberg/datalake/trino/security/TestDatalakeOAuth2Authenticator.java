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
package com.bloomberg.datalake.trino.security;

import com.google.common.collect.ImmutableMap;
import io.trino.server.security.SecurityConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.bloomberg.datalake.trino.security.DatalakeOAuth2Authenticator.findWagJwt;
import static com.bloomberg.datalake.trino.security.DatalakeOAuth2Authenticator.getNumberClaim;
import static com.bloomberg.datalake.trino.security.DatalakeOAuth2Authenticator.getOptionalNumberClaim;
import static com.bloomberg.datalake.trino.security.DatalakeOAuth2Authenticator.getOptionalStringClaim;
import static com.bloomberg.datalake.trino.security.DatalakeOAuth2Authenticator.getStringClaim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the static helper methods in {@link DatalakeOAuth2Authenticator}.
 */
public class TestDatalakeOAuth2Authenticator
{
    @Test
    void testGetStringClaimReturnsValidValue()
    {
        Map<String, Object> claims = ImmutableMap.of("username", "testuser");
        String result = getStringClaim(claims, "username");
        assertThat(result).isEqualTo("testuser");
    }

    @Test
    void testGetStringClaimThrowsForNullClaim()
    {
        assertGetStringClaimThrows(ImmutableMap.of(), "username", "Claim username is null or missing");
    }

    @Test
    void testGetStringClaimThrowsForExplicitNullClaim()
    {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", null);
        assertGetStringClaimThrows(claims, "username", "Claim username is null or missing");
    }

    @Test
    void testGetStringClaimThrowsForNonStringType()
    {
        assertGetStringClaimThrows(ImmutableMap.of("username", 123), "username", "Claim username is not a string");
    }

    @Test
    void testGetStringClaimThrowsForEmptyString()
    {
        assertGetStringClaimThrows(ImmutableMap.of("username", ""), "username", "Claim username is an empty string");
    }

    @Test
    void testGetStringClaimThrowsForWhitespaceOnlyString()
    {
        assertGetStringClaimThrows(ImmutableMap.of("username", "   "), "username", "Claim username is an empty string");
    }

    @Test
    void testGetNumberClaimReturnsLongValue()
    {
        Long result = getNumberClaim(ImmutableMap.of("id", 42L), "id");
        assertThat(result).isEqualTo(42L);
    }

    @Test
    void testGetNumberClaimReturnsIntegerAsLong()
    {
        Long result = getNumberClaim(ImmutableMap.of("id", 42), "id");
        assertThat(result).isEqualTo(42L);
    }

    @Test
    void testGetNumberClaimParsesStringNumber()
    {
        Long result = getNumberClaim(ImmutableMap.of("id", "12345"), "id");
        assertThat(result).isEqualTo(12345L);
    }

    @Test
    void testGetNumberClaimThrowsForMissingClaim()
    {
        assertGetNumberClaimThrows(ImmutableMap.of(), "id", "Claim id is null or missing");
    }

    @Test
    void testGetNumberClaimThrowsForNullValue()
    {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", null);
        assertGetNumberClaimThrows(claims, "id", "Claim id is null or missing");
    }

    @Test
    void testGetNumberClaimThrowsForInvalidString()
    {
        assertGetNumberClaimThrows(ImmutableMap.of("id", "not-a-number"), "id", "Claim id is not a valid number: not-a-number");
    }

    @Test
    void testGetNumberClaimThrowsForUnexpectedType()
    {
        assertGetNumberClaimThrows(ImmutableMap.of("id", true), "id", "Claim id has unexpected type: Boolean");
    }

    @Test
    void testGetOptionalStringClaimReturnsValueWhenPresent()
    {
        Map<String, Object> claims = ImmutableMap.of("sessionId", "session-123");
        Optional<String> result = getOptionalStringClaim(claims, "sessionId");
        assertThat(result).contains("session-123");
    }

    @Test
    void testGetOptionalStringClaimReturnsEmptyWhenMissing()
    {
        Map<String, Object> claims = ImmutableMap.of();
        Optional<String> result = getOptionalStringClaim(claims, "sessionId");
        assertThat(result).isEmpty();
    }

    @Test
    void testGetOptionalStringClaimThrowsForNullValue()
    {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sessionId", null);
        assertGetOptionalStringClaimThrows(claims, "sessionId", "Claim sessionId is null or missing");
    }

    @Test
    void testGetOptionalStringClaimThrowsForIntegerValue()
    {
        assertGetOptionalStringClaimThrows(ImmutableMap.of("sessionId", 123), "sessionId", "Claim sessionId is not a string");
    }

    @Test
    void testGetOptionalStringClaimThrowsForEmptyString()
    {
        assertGetOptionalStringClaimThrows(ImmutableMap.of("sessionId", ""), "sessionId", "Claim sessionId is an empty string");
    }

    @Test
    void testGetOptionalStringClaimThrowsForWhitespaceString()
    {
        assertGetOptionalStringClaimThrows(ImmutableMap.of("sessionId", "   "), "sessionId", "Claim sessionId is an empty string");
    }

    @Test
    void testGetOptionalNumberClaimReturnsLongValue()
    {
        Map<String, Object> claims = ImmutableMap.of("employeeid", 42L);
        Optional<Long> result = getOptionalNumberClaim(claims, "employeeid");
        assertThat(result).contains(42L);
    }

    @Test
    void testGetOptionalNumberClaimReturnsIntegerAsLong()
    {
        Map<String, Object> claims = ImmutableMap.of("employeeid", 42);
        Optional<Long> result = getOptionalNumberClaim(claims, "employeeid");
        assertThat(result).contains(42L);
    }

    @Test
    void testGetOptionalNumberClaimParsesStringNumber()
    {
        Map<String, Object> claims = ImmutableMap.of("employeeid", "12345");
        Optional<Long> result = getOptionalNumberClaim(claims, "employeeid");
        assertThat(result).contains(12345L);
    }

    @Test
    void testGetOptionalNumberClaimThrowsForEmptyString()
    {
        assertGetOptionalNumberClaimThrows(ImmutableMap.of("employeeid", ""), "employeeid", "Claim employeeid is not a valid number: ");
    }

    @Test
    void testGetOptionalNumberClaimThrowsForInvalidString()
    {
        assertGetOptionalNumberClaimThrows(ImmutableMap.of("employeeid", "not-a-number"), "employeeid", "Claim employeeid is not a valid number: not-a-number");
    }

    @Test
    void testGetOptionalNumberClaimReturnsEmptyWhenMissing()
    {
        Map<String, Object> claims = ImmutableMap.of();
        Optional<Long> result = getOptionalNumberClaim(claims, "employeeid");
        assertThat(result).isEmpty();
    }

    @Test
    void testGetOptionalNumberClaimThrowsForNullValue()
    {
        Map<String, Object> claims = new HashMap<>();
        claims.put("employeeid", null);
        assertGetOptionalNumberClaimThrows(claims, "employeeid", "Claim employeeid is null or missing");
    }

    @Test
    void testGetOptionalNumberClaimThrowsForUnexpectedType()
    {
        assertGetOptionalNumberClaimThrows(ImmutableMap.of("employeeid", true), "employeeid", "Claim employeeid has unexpected type: Boolean");
    }

    @Test
    void testFindWagJwtWithSingleHeader()
    {
        String jwtToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signature";
        Optional<String> result = findWagJwt(List.of("X-Context-Jwt=" + jwtToken));
        assertThat(result).hasValue(jwtToken);
    }

    @Test
    void testFindWagJwtWithMultipleHeaders()
    {
        String jwtToken = "jwt-token-value";
        // Multiple separate headers (standard Trino client behavior)
        Optional<String> result = findWagJwt(List.of(
                "other=value",
                "X-Context-Jwt=" + jwtToken,
                "another=test"));
        assertThat(result).hasValue(jwtToken);
    }

    @Test
    void testFindWagJwtWithCommaSeparatedCredentials()
    {
        String jwtToken = "jwt-token-value";
        // Comma-separated pairs in a single header value
        Optional<String> result = findWagJwt(List.of("other=value,X-Context-Jwt=" + jwtToken + ",another=test"));
        assertThat(result).hasValue(jwtToken);
    }

    @Test
    void testFindWagJwtNotPresent()
    {
        Optional<String> result = findWagJwt(List.of("other=value", "another=test"));
        assertThat(result).isEmpty();
    }

    @Test
    void testFindWagJwtNullInput()
    {
        Optional<String> result = findWagJwt(null);
        assertThat(result).isEmpty();
    }

    @Test
    void testFindWagJwtEmptyInput()
    {
        Optional<String> result = findWagJwt(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void testModuleCreation()
    {
        SecurityConfig securityConfig = new SecurityConfig()
                .setAuthenticationTypes(List.of("bloomberg-bsso"));
        assertThat(DatalakeOAuth2Authenticator.module(securityConfig)).isNotNull();
    }

    private void assertGetStringClaimThrows(Map<String, Object> claims, String claimName, String expectedMessage)
    {
        assertThatThrownBy(() -> getStringClaim(claims, claimName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }

    private void assertGetOptionalStringClaimThrows(Map<String, Object> claims, String claimName, String expectedMessage)
    {
        assertThatThrownBy(() -> getOptionalStringClaim(claims, claimName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }

    private void assertGetNumberClaimThrows(Map<String, Object> claims, String claimName, String expectedMessage)
    {
        assertThatThrownBy(() -> getNumberClaim(claims, claimName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }

    private void assertGetOptionalNumberClaimThrows(Map<String, Object> claims, String claimName, String expectedMessage)
    {
        assertThatThrownBy(() -> getOptionalNumberClaim(claims, claimName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }
}

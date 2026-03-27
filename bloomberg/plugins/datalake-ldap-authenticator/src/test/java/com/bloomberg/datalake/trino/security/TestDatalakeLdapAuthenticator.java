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
import io.trino.spi.security.AccessDeniedException;
import io.trino.spi.security.PasswordAuthenticator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;

import java.security.Principal;
import java.util.Map;

import static io.airlift.testing.Closeables.closeAll;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@TestInstance(PER_CLASS)
@Execution(CONCURRENT)
public class TestDatalakeLdapAuthenticator
{
    private final TestingDatalakeOpenLDAPContainer ldapContainer;
    private final PasswordAuthenticator authenticator;

    public TestDatalakeLdapAuthenticator()
    {
        ldapContainer = new TestingDatalakeOpenLDAPContainer();
        ldapContainer.start();

        Map<String, String> datalakeLdapAuthenticatorConfig = ImmutableMap.<String, String>builder()
                .put("ldap.url", ldapContainer.getLdapUrl())
                .put("ldap.allow-insecure", "true")
                .put("ldap.user-bind-pattern", "${USER}@addev.bloomberg.com")
                .put("ldap.bind-dn", "CN=admin,DC=addev,DC=bloomberg,DC=com")
                .put("ldap.bind-password", "admin")
                .put("ldap.user-base-dn", "OU=Enabled Accounts,DC=addev,DC=bloomberg,DC=com")
                .put("ldap.group-auth-pattern", "cn=${USER}")
                .buildOrThrow();
        DatalakeLdapAuthenticatorFactory factory = new DatalakeLdapAuthenticatorFactory();
        authenticator = factory.create(datalakeLdapAuthenticatorConfig);
    }

    @AfterAll
    public void close()
            throws Exception
    {
        closeAll(ldapContainer);
    }

    @Test
    public void testDatalakeLdapAuthenticator()
    {
        ldapContainer.createUser("alice", 123, "alice-pass");
        ldapContainer.createUser("bob", 456, "bob-pass");
        assertThat(authenticator.createAuthenticatedPrincipal("alice", "alice-pass")).extracting(Principal::getName).isEqualTo("bpi:bb_username:alice:bb_uuid:123");
        assertThat(authenticator.createAuthenticatedPrincipal("bob", "bob-pass")).extracting(Principal::getName).isEqualTo("bpi:bb_username:bob:bb_uuid:456");
        assertThatThrownBy(() -> authenticator.createAuthenticatedPrincipal("alice", "wrong-pass")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authenticator.createAuthenticatedPrincipal("alice", "bob-pass")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authenticator.createAuthenticatedPrincipal("invalid-user", "invalid-pass")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    public void testDatalakeLdapAuthenticatorForAccountWithoutEmployeeID()
    {
        ldapContainer.createUser("charlie", "charlie-pass");
        ldapContainer.createUser("dave", "dave-pass");
        assertThat(authenticator.createAuthenticatedPrincipal("charlie", "charlie-pass")).extracting(Principal::getName).isEqualTo("bpi:bb_username:charlie");
        assertThat(authenticator.createAuthenticatedPrincipal("dave", "dave-pass")).extracting(Principal::getName).isEqualTo("bpi:bb_username:dave");
    }
}

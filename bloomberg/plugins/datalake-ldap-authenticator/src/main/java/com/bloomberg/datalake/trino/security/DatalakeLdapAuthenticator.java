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

import com.bloomberg.datalake.bpi.DatalakeLDAPBloombergPrincipal;
import com.google.common.base.CharMatcher;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.trino.plugin.base.ldap.LdapClient;
import io.trino.plugin.base.ldap.LdapQuery;
import io.trino.plugin.password.ldap.LdapAuthenticator;
import io.trino.plugin.password.ldap.LdapAuthenticatorConfig;
import io.trino.spi.security.AccessDeniedException;
import io.trino.spi.security.PasswordAuthenticator;

import javax.naming.directory.SearchResult;

import java.security.Principal;
import java.util.Optional;

import static com.google.common.base.Verify.verify;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Password authenticator that users Trino's {@link LdapAuthenticator} to perform login
 */
public class DatalakeLdapAuthenticator
        implements PasswordAuthenticator
{
    private static final Logger log = Logger.get(DatalakeLdapAuthenticator.class);

    private static final CharMatcher SPECIAL_CHARACTERS = CharMatcher.anyOf(",=+<>#;*()\"\\\u0000");
    private static final CharMatcher WHITESPACE = CharMatcher.anyOf(" \r");

    private final LdapClient ldapClient;
    private final String userBaseDistinguishedName;
    private final String groupAuthorizationSearchPattern;
    private final String bindDistinguishedName;
    private final String bindPassword;
    private final String userIdAttributeName;

    @Inject
    public DatalakeLdapAuthenticator(LdapClient ldapClient, LdapAuthenticatorConfig config, DatalakeLdapAuthenticatorConfig datalakeLdapAuthenticatorConfig)
    {
        this.ldapClient = requireNonNull(ldapClient, "ldapClient is null");
        verify(config.getUserBindSearchPatterns().size() == 1, "Datalake Ldap authentication only supports a single user bind search pattern");
        this.userBaseDistinguishedName = requireNonNull(config.getUserBaseDistinguishedName(), "userBaseDistinguishedName is null");
        this.groupAuthorizationSearchPattern = requireNonNull(config.getGroupAuthorizationSearchPattern(), "groupAuthorizationSearchPattern is null");
        this.bindDistinguishedName = requireNonNull(config.getBindDistinguishedName(), "bindDistinguishedName is null");
        this.bindPassword = requireNonNull(config.getBindPassword(), "bindPassword is null");
        this.userIdAttributeName = datalakeLdapAuthenticatorConfig.getUserIdAttribute();
    }

    @Override
    public Principal createAuthenticatedPrincipal(String user, String password)
    {
        if (containsSpecialCharacters(user)) {
            throw new AccessDeniedException("Username cannot contain a special LDAP character");
        }

        String searchFilter = replaceUser(groupAuthorizationSearchPattern, user);

        return silentThrow(() -> {
            LdapResult ldapResult = ldapClient.executeLdapQuery(
                    bindDistinguishedName,
                    bindPassword,
                    new LdapQuery.LdapQueryBuilder()
                            .withSearchBase(userBaseDistinguishedName)
                            .withSearchFilter(searchFilter)
                            .withAttributes(userIdAttributeName)
                            .build(),
                    searchResults -> {
                        if (!searchResults.hasMore()) {
                            String message = format("User %s not a member of an authorized group", user);
                            log.debug("%s", message);
                            throw new AccessDeniedException(message);
                        }
                        SearchResult result = searchResults.next();
                        if (searchResults.hasMore()) {
                            String message = format("Multiple users found when searching for '%s'", user);
                            log.debug("%s", message);
                            throw new AccessDeniedException(message);
                        }
                        String userDistinguishedName = result.getNameInNamespace();
                        Optional<Long> userId = Optional.ofNullable(result.getAttributes().get(userIdAttributeName))
                                .map(userIdAttribute -> {
                                    verify(userIdAttribute.size() == 1, "User %s has multiple %s attributes", user, userIdAttributeName);
                                    return silentThrow(() -> userIdAttribute.get().toString(), user);
                                })
                                .map(Long::parseLong);
                        return new LdapResult(userDistinguishedName, new DatalakeLDAPBloombergPrincipal(user, userId));
                    });
            ldapClient.processLdapContext(ldapResult.userDistinguishedName(), password, _ -> null);
            return ldapResult.principal();
        }, user);
    }

    private static String replaceUser(String pattern, String user)
    {
        return pattern.replace("${USER}", user);
    }

    private static boolean containsSpecialCharacters(String user)
    {
        if (WHITESPACE.indexIn(user) == 0 || WHITESPACE.lastIndexIn(user) == user.length() - 1) {
            return true;
        }
        return SPECIAL_CHARACTERS.matchesAnyOf(user);
    }

    private static <R> R silentThrow(ThrowingSupplier<R> supplier, String user)
    {
        try {
            return supplier.get();
        }
        catch (Exception e) {
            log.warn(e, "Authentication failed for user [%s], %s", user, e.getMessage());
            throw new AccessDeniedException("Authentication error");
        }
    }

    private interface ThrowingSupplier<R>
    {
        R get()
                throws Exception;
    }

    private record LdapResult(String userDistinguishedName, Principal principal)
    {
        LdapResult
        {
            requireNonNull(userDistinguishedName, "userDistinguishedName is null");
            requireNonNull(principal, "principal is null");
        }
    }
}

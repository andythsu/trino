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

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Scopes;
import io.trino.server.security.AuthenticationException;
import io.trino.server.security.Authenticator;
import io.trino.server.security.BasicAuthCredentials;
import io.trino.server.security.PasswordAuthenticatorConfig;
import io.trino.server.security.PasswordAuthenticatorManager;
import io.trino.server.security.SecurityConfig;
import io.trino.spi.security.AccessDeniedException;
import io.trino.spi.security.Identity;
import io.trino.spi.security.PasswordAuthenticator;
import jakarta.ws.rs.container.ContainerRequestContext;

import java.security.Principal;
import java.util.List;

import static com.google.common.base.Verify.verify;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static io.trino.server.security.BasicAuthCredentials.extractBasicAuthCredentials;
import static io.trino.server.security.ServerSecurityModule.authenticatorModule;
import static java.util.Objects.requireNonNull;

public class DatalakePasswordAuthenticator
        implements Authenticator
{
    private final PasswordAuthenticatorManager passwordAuthenticatorManager;
    private final UserHeaderRewriter userHeaderRewriter;

    @Inject
    public DatalakePasswordAuthenticator(PasswordAuthenticatorManager passwordAuthenticatorManager, UserHeaderRewriter userHeaderRewriter)
    {
        this.passwordAuthenticatorManager = passwordAuthenticatorManager;
        passwordAuthenticatorManager.setRequired();
        this.userHeaderRewriter = requireNonNull(userHeaderRewriter, "userHeaderRewriter is null");
    }

    @Override
    public Identity authenticate(ContainerRequestContext request)
            throws AuthenticationException
    {
        BasicAuthCredentials basicAuthCredentials = extractBasicAuthCredentials(request)
                .orElseThrow(() -> new AuthenticationException(null, BasicAuthCredentials.AUTHENTICATE_HEADER));
        String user = basicAuthCredentials.getUser();
        String password = basicAuthCredentials.getPassword()
                .orElseThrow(() -> new AuthenticationException("Malformed credentials: password is empty"));

        List<PasswordAuthenticator> authenticators = ImmutableList.copyOf(passwordAuthenticatorManager.getAuthenticators());

        AuthenticationException exception = null;
        for (PasswordAuthenticator authenticator : authenticators) {
            try {
                Principal principal = authenticator.createAuthenticatedPrincipal(user, password);
                Identity identity = Identity.forUser(principal.getName())
                        .withPrincipal(principal)
                        .build();
                userHeaderRewriter.rewriteUserHeaders(identity, request.getHeaders());
                return identity;
            }
            catch (AccessDeniedException e) {
                if (exception == null) {
                    exception = needAuthentication(e.getMessage());
                }
                else {
                    exception.addSuppressed(needAuthentication(e.getMessage()));
                }
                // Save the exception and move on
            }
            catch (RuntimeException e) {
                throw new RuntimeException("Authentication error", e);
            }
        }
        verify(exception != null, "No exception was thrown during authentication");
        throw exception;
    }

    public static Module module(SecurityConfig securityConfig)
    {
        return authenticatorModule(securityConfig, "bloomberg-password", DatalakePasswordAuthenticator.class, binder -> {
            configBinder(binder).bindConfig(PasswordAuthenticatorConfig.class);
            binder.bind(PasswordAuthenticatorManager.class).in(Scopes.SINGLETON);
        });
    }

    private static AuthenticationException needAuthentication(String message)
    {
        return new AuthenticationException(message, BasicAuthCredentials.AUTHENTICATE_HEADER);
    }
}

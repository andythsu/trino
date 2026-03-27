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

import com.bloomberg.datalake.bpi.DatalakeBSSOBloombergPrincipal;
import com.bloomberg.datalake.bpi.DatalakeLDAPBloombergPrincipal;
import com.bloomberg.datalake.bpi.DatalakePrincipal;
import com.bloomberg.datalake.bpi.DatalakeWAGPrincipal;
import com.google.inject.Inject;
import io.trino.client.ProtocolDetectionException;
import io.trino.server.ProtocolConfig;
import io.trino.spi.security.Identity;
import jakarta.ws.rs.core.MultivaluedMap;

import java.security.Principal;
import java.util.Optional;

import static io.trino.client.ProtocolHeaders.detectProtocol;

public class UserHeaderRewriter
{
    private final Optional<String> alternateHeaderName;

    @Inject
    public UserHeaderRewriter(ProtocolConfig protocolConfig)
    {
        this.alternateHeaderName = protocolConfig.getAlternateHeaderName();
    }

    /**
     * <p>The Trino client protocol uses 2 headers to signal the user that the session is attempting to assume.</p>
     * <ul>
     *   <li>X-Trino-User - The session user that queries run as. Access control checks are done for this user;</li>
     *   <li>X-Trino-Original-User - User that authenticated and is attempting to impersonate X-Trino-User;</li>
     * </ul>
     * <p>When impersonation is not used these have the same value.</p>
     * <p>These are not required - if omitted the authenticated username is used - but take precedence over the
     * authenticated username when provided. If present and matching the authenticated username these have no effect.</p>
     * <p>Since the custom Datalake authenticators overrides usernames with BPIs these also have to be changed to the BPI iff they match
     * the username.</p>
     */
    public void rewriteUserHeaders(Identity authenticatedIdentity, MultivaluedMap<String, String> headers)
    {
        Principal principal = authenticatedIdentity
                .getPrincipal()
                .orElseThrow(() -> new IllegalStateException("No Principal provided for Identity created by a Datalake Authenticator"));
        String authenticatedUser = principal.getName();
        if (!(principal instanceof DatalakePrincipal datalakePrincipal)) {
            return;
        }
        Optional<?> maybeRequestUser = switch (datalakePrincipal) {
            case DatalakeLDAPBloombergPrincipal(String username, var _) -> Optional.of(username);
            case DatalakeBSSOBloombergPrincipal(String username, var _, var _) -> Optional.of(username);
            case DatalakeWAGPrincipal wagPrincipal -> Optional.of(wagPrincipal.getName());
        };
        maybeRequestUser.ifPresent(requestUser -> {
            String userHeader;
            String originalUserHeader;
            try {
                originalUserHeader = detectProtocol(alternateHeaderName, headers.keySet()).requestOriginalUser();
                userHeader = detectProtocol(alternateHeaderName, headers.keySet()).requestUser();
            }
            catch (ProtocolDetectionException _) {
                // this shouldn't fail here, but ignore, and it will be handled elsewhere
                return;
            }
            if (requestUser.equals(headers.getFirst(userHeader))) {
                headers.putSingle(userHeader, authenticatedUser);
            }
            if (requestUser.equals(headers.getFirst(originalUserHeader))) {
                headers.putSingle(originalUserHeader, authenticatedUser);
            }
        });
    }
}

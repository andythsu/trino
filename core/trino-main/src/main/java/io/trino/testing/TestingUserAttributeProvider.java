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
package io.trino.testing;

import com.google.common.collect.ImmutableMap;
import io.trino.spi.security.UserAttributeProvider;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

public class TestingUserAttributeProvider
        implements UserAttributeProvider
{
    private final Map<UserPrincipal, Map<String, Object>> userAttributes = new ConcurrentHashMap<>();

    public void reset()
    {
        userAttributes.clear();
    }

    public void setUserAttributes(String user, Optional<Principal> principal, Map<String, Object> attributes)
    {
        userAttributes.put(new UserPrincipal(user, principal), ImmutableMap.copyOf(attributes));
    }

    @Override
    public Map<String, Object> getUserAttributes(String user, Optional<Principal> principal)
    {
        return userAttributes.getOrDefault(new UserPrincipal(user, principal), ImmutableMap.of());
    }

    private record UserPrincipal(String user, Optional<Principal> principal)
    {
        UserPrincipal
        {
            requireNonNull(user, "user is null");
            requireNonNull(principal, "principal is null");
        }
    }
}

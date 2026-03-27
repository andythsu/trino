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
package io.trino.security;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import io.airlift.log.Logger;
import io.trino.spi.classloader.ThreadContextClassLoader;
import io.trino.spi.security.UserAttributeProvider;
import io.trino.spi.security.UserAttributeProviderFactory;

import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static io.airlift.configuration.ConfigurationLoader.loadPropertiesFrom;
import static java.util.Objects.requireNonNull;

public class UserAttributeProviderManager
        implements UserAttributeProvider
{
    private static final Logger log = Logger.get(UserAttributeProviderManager.class);

    private static final File USER_ATTRIBUTE_PROVIDER_CONFIGURATION = new File("etc/user-attribute-provider.properties");
    private static final String USER_ATTRIBUTE_PROVIDER_NAME = "user-attribute-provider.name";

    private final Map<String, UserAttributeProviderFactory> userAttributeProviderFactories = new ConcurrentHashMap<>();
    private final AtomicReference<Optional<UserAttributeProvider>> configuredUserAttributeProvider = new AtomicReference<>(Optional.empty());

    public void addUserAttributeProviderFactory(UserAttributeProviderFactory factory)
    {
        requireNonNull(factory, "factory is null");

        if (userAttributeProviderFactories.putIfAbsent(factory.getName(), factory) != null) {
            throw new IllegalArgumentException("User attribute provider " + factory.getName() + " is already registered");
        }
    }

    public void loadConfiguredAttributeProvider()
            throws IOException
    {
        if (configuredUserAttributeProvider.get().isPresent() || !USER_ATTRIBUTE_PROVIDER_CONFIGURATION.exists()) {
            return;
        }
        Map<String, String> properties = new HashMap<>(loadPropertiesFrom(USER_ATTRIBUTE_PROVIDER_CONFIGURATION.getPath()));

        String userAttributeProviderName = properties.remove(USER_ATTRIBUTE_PROVIDER_NAME);
        checkArgument(!isNullOrEmpty(userAttributeProviderName),
                "User attribute provider configuration %s does not contain %s", USER_ATTRIBUTE_PROVIDER_CONFIGURATION.getAbsolutePath(), USER_ATTRIBUTE_PROVIDER_NAME);

        setConfiguredUserAttributeProvider(userAttributeProviderName, properties);
    }

    @VisibleForTesting
    protected void setConfiguredUserAttributeProvider(String name, Map<String, String> properties)
    {
        log.info("-- Loading user attribute provider %s --", name);

        UserAttributeProviderFactory factory = userAttributeProviderFactories.get(name);
        checkState(factory != null, "User attribute provider %s not registered", name);

        UserAttributeProvider userAttributeProvider;
        try (ThreadContextClassLoader _ = new ThreadContextClassLoader(Thread.currentThread().getContextClassLoader())) {
            userAttributeProvider = factory.create(properties);
        }

        setConfiguredUserAttributeProvider(userAttributeProvider);

        log.info("-- Loaded user attribute provider %s --", name);
    }

    @VisibleForTesting
    protected void setConfiguredUserAttributeProvider(UserAttributeProvider userAttributeProvider)
    {
        checkState(configuredUserAttributeProvider.compareAndSet(Optional.empty(), Optional.of(userAttributeProvider)), "userAttributeProvider is already set");
    }

    @Override
    public Map<String, Object> getUserAttributes(String user, Optional<Principal> principal)
    {
        requireNonNull(user, "user is null");
        requireNonNull(principal, "principal is null");
        return configuredUserAttributeProvider.get()
                .map(provider -> provider.getUserAttributes(user, principal))
                .map(ImmutableMap::copyOf)
                .orElse(ImmutableMap.of());
    }
}

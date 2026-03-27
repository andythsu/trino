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

import io.trino.security.UserAttributeProviderManager;
import io.trino.spi.security.UserAttributeProvider;

import java.util.Map;

public class TestingUserAttributeProviderManager
        extends UserAttributeProviderManager
{
    @Override
    public void setConfiguredUserAttributeProvider(String name, Map<String, String> properties)
    {
        super.setConfiguredUserAttributeProvider(name, properties);
    }

    @Override
    public void setConfiguredUserAttributeProvider(UserAttributeProvider userAttributeProvider)
    {
        super.setConfiguredUserAttributeProvider(userAttributeProvider);
    }
}

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
package com.bloomberg.datalake.trino.plugin.bas.jinja;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.lib.filter.Filter;

import java.util.Set;

public class JinjaProvider
        implements Provider<Jinjava>
{
    private Set<Filter> jinjaFilters;

    @Inject
    public JinjaProvider()
    {
    }

    @Inject(optional = true)
    public JinjaProvider setJinjaFilters(Set<Filter> jinjaFilters)
    {
        this.jinjaFilters = jinjaFilters;
        return this;
    }

    @Override
    public Jinjava get()
    {
        Jinjava jinja = new Jinjava();
        if (jinjaFilters != null) {
            jinjaFilters.forEach(jinja.getGlobalContext()::registerFilter);
        }
        return jinja;
    }
}

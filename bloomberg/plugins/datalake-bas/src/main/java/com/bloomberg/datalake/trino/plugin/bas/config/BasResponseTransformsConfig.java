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
package com.bloomberg.datalake.trino.plugin.bas.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public class BasResponseTransformsConfig
{
    private final List<BasTransformAction> actions;

    @JsonCreator
    public BasResponseTransformsConfig(
            @JsonProperty("actions") List<BasTransformAction> actions)
    {
        this.actions = actions;
    }

    @JsonProperty("actions")
    public List<BasTransformAction> getActions()
    {
        return actions;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BasResponseTransformsConfig that = (BasResponseTransformsConfig) o;
        return Objects.equals(actions, that.actions);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(actions);
    }
}

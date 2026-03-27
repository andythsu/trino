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
package com.bloomberg.datalake.trino.plugin.bas.transforms;

import com.bloomberg.datalake.trino.plugin.bas.config.BasTransformAction;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.bloomberg.datalake.trino.plugin.bas.transforms.BasResponseTransformer.flatten;
import static java.util.Objects.requireNonNull;

/**
 * <p>Unnests each row from a list of rows by taking each collection identified by the key.</p>
 * <code>
 * [
 * {
 * "a": "v1",
 * "b": [
 * "v2",
 * "v3"
 * ]
 * }
 * ]
 * </code>
 * <p>becomes</p>
 * <code>
 * [
 * {
 * "a": "v1",
 * "b": "v2"
 * },
 * {
 * "a": "v1",
 * "b": "v3"
 * }
 * ]
 * </code>
 * <p>when key = b</p>
 *
 * @return A new List of Maps with the unnested input. This map is only a shallow copy of the input.
 */
public class BasUnnestTransformAction
        implements BasTransformAction
{
    private final String key;

    @JsonCreator
    public BasUnnestTransformAction(
            @JsonProperty("key") String key)
    {
        this.key = requireNonNull(key, "key is null");
    }

    @JsonProperty("key")
    public String getKey()
    {
        return key;
    }

    @Override
    public List<Map<String, Object>> apply(List<Map<String, Object>> rows)
    {
        ImmutableList.Builder<Map<String, Object>> resultBuilder = ImmutableList.builder();

        for (var row : rows) {
            if (!(row.containsKey(key) && row.get(key) instanceof Collection)) {
                resultBuilder.add(row);
                continue;
            }
            Collection<Object> collection = (Collection<Object>) row.get(key);
            Map<String, Object> filteredMap = Maps.filterKeys(row, s -> !Objects.equals(s, key));

            if (collection.isEmpty()) {
                resultBuilder.add(ImmutableMap.<String, Object>builder().putAll(filteredMap).buildOrThrow());
                continue;
            }

            for (var entry : collection) {
                resultBuilder.add(flatten(ImmutableMap.<String, Object>builder().putAll(filteredMap).put(key, entry).buildOrThrow()));
            }
        }

        return resultBuilder.build();
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
        BasUnnestTransformAction that = (BasUnnestTransformAction) o;
        return Objects.equals(key, that.key);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(key);
    }

    @Override
    public String toString()
    {
        return "BasUnnestTransformAction{" +
                "key='" + key + '\'' +
                '}';
    }
}

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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.bloomberg.datalake.trino.plugin.bas.transforms.BasResponseTransformer.flatten;
import static java.util.Objects.requireNonNull;

/**
 * <p>Spreads a list of rows by splitting the Collection at key in each row into entries <code>key[x] = collection.get(x)</code>.
 * Elements of the Collection which are Maps will be flattened.</p>
 * <code>
 * {
 * "array": [
 * {
 * "a": "v1"
 * },
 * {
 * "a": "v2"
 * }
 * ]
 * }
 * </code>
 * <p>becomes</p>
 * <code>
 * [
 * {
 * "array[0].a": "v1",
 * "array[1].a": "v2"
 * }
 * ]
 * </code>
 */
public class BasSpreadTransformAction
        implements BasTransformAction
{
    private final String key;

    @JsonCreator
    public BasSpreadTransformAction(
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
            Iterator<Object> iterator = ((Collection<Object>) row.get(key)).iterator();

            Map<String, Object> filteredMap = Maps.filterKeys(row, s -> !Objects.equals(s, key));

            // If the map is empty the rest of the map stays instact
            if (!iterator.hasNext()) {
                resultBuilder.add(ImmutableMap.<String, Object>builder().putAll(filteredMap).buildOrThrow());
                continue;
            }

            ImmutableMap.Builder<String, Object> rowBuilder = ImmutableMap.<String, Object>builder().putAll(filteredMap);
            int i = 0;
            while (iterator.hasNext()) {
                rowBuilder.put(String.format("%s[%s]", key, i++), iterator.next());
            }
            resultBuilder.add(flatten(rowBuilder.buildOrThrow()));
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
        BasSpreadTransformAction that = (BasSpreadTransformAction) o;
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
        return "BasSpreadTransformAction{" +
                "key='" + key + '\'' +
                '}';
    }
}

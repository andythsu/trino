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

import com.bloomberg.datalake.trino.plugin.bas.config.BasResponseTransformsConfig;
import com.bloomberg.datalake.trino.plugin.bas.config.BasTransformAction;
import com.google.common.collect.ImmutableMap;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BasResponseTransformer
{
    private static final String DELIMITER = ".";

    private final BasResponseTransformsConfig transformConfig;

    public BasResponseTransformer(BasResponseTransformsConfig transformConfig)
    {
        this.transformConfig = transformConfig;
    }

    /**
     * <p>Flattens nested Maps into a structure as flat as possible.</p>
     * <p>Collections are not flattened themselves, but all elements inside the collection are flattened recursively</p>
     *
     * <code>
     * {
     * "a": {
     * "b": "v1"
     * },
     * "c": [
     * {
     * "d": {
     * "e": "v2"
     * }
     * },
     * "v3"
     * ]
     * }
     * </code>
     * <p>becomes</p>
     * <code>
     * {
     * "a.b": "v1",
     * "c": [
     * {
     * "d.e": "v2"
     * },
     * "v3"
     * ]
     * }
     * </code>
     *
     * @return A new Map where entries are either non-Map types or Collection types, where the elements are either Map-types, Collection-types or other types.
     */
    public static Map<String, Object> flatten(Map<String, Object> map)
    {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.<String, Object>builder();

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();

            if (val == null) {
                continue;
            }

            if (val instanceof Map) {
                Map<String, Object> innerMap = flatten((Map<String, Object>) val);
                for (Map.Entry<String, Object> innerEntry : innerMap.entrySet()) {
                    builder.put(key + DELIMITER + innerEntry.getKey(), innerEntry.getValue());
                }
            }
            else if (val instanceof Collection) {
                builder.put(key, flattenCollection((Collection<Object>) val));
            }
            else {
                builder.put(key, map.get(key));
            }
        }

        return builder.buildOrThrow();
    }

    private static Collection<Object> flattenCollection(Collection<Object> collection)
    {
        return collection.stream().map(element -> {
            if (element instanceof Collection) {
                return flattenCollection((Collection<Object>) element);
            }
            else if (element instanceof Map) {
                return flatten((Map<String, Object>) element);
            }
            else {
                return element;
            }
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> transform(Map<String, Object> data)
    {
        List<Map<String, Object>> td = List.of(flatten(data));
        for (BasTransformAction action : transformConfig.getActions()) {
            td = action.apply(td);
        }
        return td;
    }
}

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * <p>
 * For a list of rows, this action renames the {@code key}, in all rows which contain it,
 * to the {@code newName}. For example, given:
 * </p>
 * <code>
 * key = 'foo';<br/>
 * newName = 'bar';<br/>
 * rows = [{'foo': 1, 'xyz': 2}, {'abc': 1, 'xyz': 2}];
 * </code>
 * <p>then:</p>
 * <code>
 * rows = [{'bar': 1, 'xyz': 2}, {'abc': 1, 'xyz': 2}];
 * </code>
 * <p>
 *     <strong>Notes:</strong>
 *     <ul>
 *         <li>
 *             {@code key} cannot be the same as {@code newName} in the configuration.
 *         </li>
 *         <li>
 *             At query-time, an error will be thrown if any row in the result contains both
 *             {@code key} and {@code newName} (because the transformer won't know which value to keep).
 *         </li>
 *         <li>
 *             The renaming will only be done at the first level in the list of rows. Any maps containing
 *             {@code key} at all levels except the first will remain unaffected.
 *         </li>
 *         <li>
 *             No flattening happens at any stage during this transform action.
 *         </li>
 *     </ul>
 * </p>
 */
public class BasRenameTransformAction
        implements BasTransformAction
{
    private final String key;
    private final String newName;

    @JsonCreator
    public BasRenameTransformAction(@JsonProperty("key") String key, @JsonProperty("newName") String newName)
    {
        this.key = requireNonNull(key, "key is null");
        this.newName = requireNonNull(newName, "newName is null");

        // key and newName can't be the same.
        if (this.newName.equals(this.key)) {
            throw new IllegalArgumentException(String.format("newName is same as key: `%s`", newName));
        }
    }

    /**
     * The field in the rows which needs to be renamed.
     */
    @JsonProperty("key")
    public String getKey()
    {
        return key;
    }

    /**
     * The new name of the field identified originally by {@code key}.
     */
    @JsonProperty("newName")
    public String getNewName()
    {
        return newName;
    }

    /**
     * Renames {@code key} to {@code newName} in a list of {@code rows}.
     *
     * @param rows The list of rows in which the renaming is to be done.
     * @return The new list of rows with {@code key} renamed to {@code newName}.
     */
    @Override
    public List<Map<String, Object>> apply(List<Map<String, Object>> rows)
    {
        ImmutableList.Builder<Map<String, Object>> resultBuilder = ImmutableList.builder();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);

            if (!row.containsKey(key)) {
                resultBuilder.add(row);
                continue;
            }

            if (row.containsKey(newName)) {
                String err = String.format("Row with index `%d` already contains a key named `%s`", i, newName);
                throw new IllegalArgumentException(err);
            }

            Map<String, Object> newRow = new HashMap<>(row);  // We need a `row` to be a mutable map.
            newRow.put(newName, newRow.remove(key));  // Rename the ``key`` to ``newName``.
            resultBuilder.add(newRow);
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
        BasRenameTransformAction that = (BasRenameTransformAction) o;
        return Objects.equals(key, that.key) && Objects.equals(newName, that.newName);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(key, newName);
    }

    @Override
    public String toString()
    {
        return String.format("BasRenameTransformAction{key='%s', newName='%s'}", key, newName);
    }
}

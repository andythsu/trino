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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.bloomberg.datalake.trino.plugin.bas.transforms.BasResponseTransformer.flatten;
import static java.util.Objects.requireNonNull;

/**
 * <p>Zips columns to create new rows from every row. This is similar to unnesting multiple keys at the same time. This is mostly useful for Collections which have the same
 * length, but this will handle different lengths by creating as many new records as the maximum length of the lists given by keys and leaving keys unset for smaller
 * Collections</p>
 *
 * <code>
 * [
 * {
 * "array": [
 * {
 * "a": "v1"
 * },
 * {
 * "a": "v2"
 * }
 * ],
 * "array2": [
 * {
 * "b": "v3"
 * },
 * {
 * "b": "v4"
 * }
 * ]
 * }
 * ]
 * </code>
 * <p>becomes</p>
 * <code>
 * [
 * {
 * "array.a": "v1",
 * "array2.b": "v3"
 * },
 * {
 * "array.a": "v2",
 * "array2.b": "v4"
 * }
 * ]
 * </code>
 *
 * @return Returns a new List of Maps with the output, which is only a shallow copy of the input rows
 */
public class BasZipTransformAction
        implements BasTransformAction
{
    private final List<Zipper> zippers;

    @JsonCreator
    public BasZipTransformAction(
            @JsonProperty("zippers") List<Zipper> zippers)
    {
        this.zippers = requireNonNull(zippers, "zippers cannot be null");
    }

    @JsonProperty("zippers")
    public List<Zipper> getZippers()
    {
        return zippers;
    }

    @Override
    public List<Map<String, Object>> apply(List<Map<String, Object>> rows)
    {
        ImmutableList.Builder<Map<String, Object>> resultBuilder = ImmutableList.builder();

        for (Map<String, Object> row : rows) {
            var zipperResults = zippers.stream().map(z -> z.zip(row)).collect(Collectors.toUnmodifiableList());
            int maxLength = zipperResults.stream().map(List::size).max(Integer::compare).get();

            ImmutableList.Builder<Map<String, Object>> zippedBuilder = ImmutableList.builder();
            for (int i = 0; i < maxLength; ++i) {
                ImmutableMap.Builder<String, Object> rowBuilder = ImmutableMap.builder();
                for (List<Map.Entry<String, Object>> zipperRes : zipperResults) {
                    if (zipperRes.size() - 1 >= i) {
                        rowBuilder.put(zipperRes.get(i));
                    }
                }
                zippedBuilder.add(rowBuilder.buildOrThrow());
            }
            List<Map<String, Object>> zippedData = zippedBuilder.build();

            // This is a view of row, Maps.filterKeys does not create a new Map, it stops keys that don't pass the predicate from being visible
            Predicate<String> combinedPred = zippers.stream().map(Zipper::getKeysPredicate).reduce(x -> false, Predicate::or);
            Map<String, Object> filteredMap = Maps.filterKeys(row, combinedPred.negate()::test);

            for (Map<String, Object> zippedDatum : zippedData) {
                Map<String, Object> newRow = flatten(ImmutableMap.<String, Object>builder().putAll(filteredMap).putAll(zippedDatum).buildOrThrow());
                resultBuilder.add(newRow);
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
        BasZipTransformAction that = (BasZipTransformAction) o;
        return Objects.equals(zippers, that.zippers);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(zippers);
    }

    @Override
    public String toString()
    {
        return "BasZipTransformAction{" +
                "zippers=" + zippers +
                '}';
    }

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = SingleKeyZipper.class, name = "singleKey"),
            @JsonSubTypes.Type(value = ListZipper.class, name = "list")})
    public interface Zipper
    {
        Predicate<String> getKeysPredicate();

        List<Map.Entry<String, Object>> zip(Map<String, Object> data);
    }

    public static class SingleKeyZipper
            implements Zipper
    {
        private final String key;

        @JsonCreator
        public SingleKeyZipper(
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
        public Predicate<String> getKeysPredicate()
        {
            return key::equals;
        }

        @Override
        public List<Map.Entry<String, Object>> zip(Map<String, Object> data)
        {
            if (!data.containsKey(key)) {
                return List.of();
            }
            else {
                List<Object> list = (List<Object>) data.get(key);
                return list.stream().map(e -> Map.entry(key, e)).collect(Collectors.toUnmodifiableList());
            }
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
            SingleKeyZipper that = (SingleKeyZipper) o;
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
            return "SingleKeyZipper{" +
                    "key='" + key + '\'' +
                    '}';
        }
    }

    public static class ListZipper
            implements Zipper
    {
        private final String key;
        private final Pattern pattern;

        @JsonCreator
        public ListZipper(
                @JsonProperty("key") String key,
                @JsonProperty("pattern") Pattern pattern)
        {
            this.key = requireNonNull(key, "key is null");
            this.pattern = requireNonNull(pattern, "pattern is null");
        }

        @JsonProperty("key")
        public String getKey()
        {
            return key;
        }

        @JsonProperty("pattern")
        public Pattern getPattern()
        {
            return pattern;
        }

        @Override
        public Predicate<String> getKeysPredicate()
        {
            return pattern.asPredicate();
        }

        @Override
        public List<Map.Entry<String, Object>> zip(Map<String, Object> data)
        {
            List<List<Object>> matchingLists = data.entrySet().stream()
                    .filter(e -> pattern.asPredicate().test(e.getKey()))
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> (List<Object>) e.getValue())
                    .collect(Collectors.toUnmodifiableList());

            int maxLength = matchingLists.stream().map(List::size).max(Integer::compare).orElse(0);

            return IntStream.range(0, maxLength)
                    .mapToObj(i -> Map.entry(key, (Object) matchingLists.stream()
                            .map(l -> {
                                if (l.size() - 1 >= i) {
                                    return l.get(i);
                                }
                                else {
                                    return null;
                                }
                            })
                            .collect(Collectors.toList())))
                    .collect(Collectors.toUnmodifiableList());
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
            ListZipper that = (ListZipper) o;
            return Objects.equals(key, that.key) && Objects.equals(pattern.pattern(), that.pattern.pattern());
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(key, pattern);
        }

        @Override
        public String toString()
        {
            return "ListZipper{" +
                    "key='" + key + '\'' +
                    ", pattern=" + pattern +
                    '}';
        }
    }
}

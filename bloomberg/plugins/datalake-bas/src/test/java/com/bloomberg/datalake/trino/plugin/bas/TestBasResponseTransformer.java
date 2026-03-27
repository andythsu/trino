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
package com.bloomberg.datalake.trino.plugin.bas;

import com.bloomberg.datalake.trino.plugin.bas.config.BasResponseTransformsConfig;
import com.bloomberg.datalake.trino.plugin.bas.config.BasTransformAction;
import com.bloomberg.datalake.trino.plugin.bas.transforms.BasRenameTransformAction;
import com.bloomberg.datalake.trino.plugin.bas.transforms.BasResponseTransformer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import io.airlift.json.JsonCodec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestBasResponseTransformer
{
    private final List<TestConfiguration> configurations;

    TestBasResponseTransformer()
            throws IOException
    {
        JsonCodec<List<TestConfiguration>> codec = JsonCodec.listJsonCodec(TestConfiguration.class);
        String jsonText = Resources.toString(Resources.getResource("transforms.json"), StandardCharsets.UTF_8);
        configurations = codec.fromJson(jsonText);
    }

    @Test
    void testResponseTransformation()
    {
        for (TestConfiguration config : configurations) {
            assertResponseTransformation(config.given(), config.transformations(), config.expected());
        }
    }

    private void assertResponseTransformation(Map<String, Object> given, List<BasTransformAction> transforms, List<Map<String, Object>> expected)
    {
        BasResponseTransformer responseTransformer = new BasResponseTransformer(new BasResponseTransformsConfig(transforms));
        List<Map<String, Object>> result = responseTransformer.transform(given);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void testRenameFailsWhenNewNameExists()
    {
        // Given
        Map<String, Object> given = ImmutableMap.ofEntries(entry("foo", "123"), entry("bar", "456"));

        // Transforms
        BasRenameTransformAction renameAction = new BasRenameTransformAction("foo", "bar");
        List<BasTransformAction> transformations = ImmutableList.of(renameAction);

        // Should throw IllegalArgumentException
        BasResponseTransformer responseTransformer = new BasResponseTransformer(new BasResponseTransformsConfig(transformations));
        assertThatThrownBy(() -> {
            responseTransformer.transform(given);
        }).isInstanceOf(IllegalArgumentException.class);
    }

    public record TestConfiguration(
            Map<String, Object> given,
            List<BasTransformAction> transformations,
            List<Map<String, Object>> expected)
    { }
}

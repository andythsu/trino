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

import com.bloomberg.datalake.trino.plugin.bas.transforms.BasRenameTransformAction;
import com.bloomberg.datalake.trino.plugin.bas.transforms.BasSpreadTransformAction;
import com.bloomberg.datalake.trino.plugin.bas.transforms.BasUnnestTransformAction;
import com.bloomberg.datalake.trino.plugin.bas.transforms.BasZipTransformAction;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;
import java.util.Map;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = BasSpreadTransformAction.class, name = "spread"),
        @JsonSubTypes.Type(value = BasUnnestTransformAction.class, name = "unnest"),
        @JsonSubTypes.Type(value = BasZipTransformAction.class, name = "zip"),
        @JsonSubTypes.Type(value = BasRenameTransformAction.class, name = "rename"),
})
public interface BasTransformAction
{
    List<Map<String, Object>> apply(List<Map<String, Object>> rows);
}

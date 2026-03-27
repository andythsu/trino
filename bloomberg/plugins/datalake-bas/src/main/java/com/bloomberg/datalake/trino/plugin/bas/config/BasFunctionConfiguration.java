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

import com.bloomberg.datalake.trino.plugin.bas.BasColumnHandle;
import com.bloomberg.datalake.trino.plugin.bas.errordetection.BasErrorDetector;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BasFunctionConfiguration
{
    private final String name;
    private final List<BasColumnHandle> columns;
    private final List<BasColumnHandle> parameterColumns;
    private final String requestTemplate;
    private final BasResponseTransformsConfig responseTransforms;
    private final List<BasErrorDetector> basErrorDetectors;

    @JsonCreator
    public BasFunctionConfiguration(
            @JsonProperty("name") String name,
            @JsonProperty("columns") List<BasColumnHandle> columns,
            @JsonProperty("parameterColumns") List<BasColumnHandle> parameterColumns,
            @JsonProperty("requestTemplate") String requestTemplate,
            @JsonProperty("responseTransforms") BasResponseTransformsConfig responseTransforms,
            @JsonProperty("errorDetectors") List<BasErrorDetector> errorDetectors)
    {
        this.name = name;
        this.columns = columns;
        this.parameterColumns = parameterColumns;
        this.requestTemplate = requestTemplate;
        this.responseTransforms = responseTransforms;
        this.basErrorDetectors = errorDetectors;
    }

    @JsonProperty("name")
    public String getName()
    {
        return name;
    }

    @JsonProperty("columns")
    public List<BasColumnHandle> getColumns()
    {
        return columns;
    }

    @JsonProperty("parameterColumns")
    public List<BasColumnHandle> getParameterColumns()
    {
        return parameterColumns;
    }

    public List<BasColumnHandle> getAllColumns()
    {
        return Stream.concat(getColumns().stream(), getParameterColumns().stream()).collect(Collectors.toUnmodifiableList());
    }

    @JsonProperty("requestTemplate")
    public String getRequestTemplate()
    {
        return requestTemplate;
    }

    @JsonProperty("responseTransforms")
    public BasResponseTransformsConfig getResponseTransforms()
    {
        return responseTransforms;
    }

    @JsonProperty("errorDetectors")
    public List<BasErrorDetector> getBasErrorDetectors()
    {
        return basErrorDetectors;
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
        BasFunctionConfiguration that = (BasFunctionConfiguration) o;
        return Objects.equals(name, that.name) && Objects.equals(columns, that.columns) && Objects.equals(parameterColumns, that.parameterColumns) && Objects.equals(requestTemplate, that.requestTemplate) && Objects.equals(responseTransforms, that.responseTransforms);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name, columns, parameterColumns, requestTemplate, responseTransforms);
    }
}

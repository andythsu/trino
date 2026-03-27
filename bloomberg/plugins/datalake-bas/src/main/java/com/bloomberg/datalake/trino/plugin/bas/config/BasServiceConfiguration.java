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

import com.bloomberg.basreactor.external.basmessage.types.ServiceInformation;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public class BasServiceConfiguration
{
    private final String name;
    private final ServiceInformation serviceInfo;
    private final List<BasFunctionConfiguration> functions;

    public BasServiceConfiguration(String name, ServiceInformation serviceInformation, List<BasFunctionConfiguration> functions)
    {
        this.name = name;
        this.serviceInfo = serviceInformation;
        this.functions = functions;
    }

    @JsonCreator
    public BasServiceConfiguration(
            @JsonProperty("name") String name,
            @JsonProperty("serviceInfo") String serviceInfoString,
            @JsonProperty("functions") List<BasFunctionConfiguration> functions)
    {
        this(name, ServiceInformation.parse(serviceInfoString), functions);
    }

    @JsonProperty("name")
    public String getName()
    {
        return name;
    }

    @JsonProperty("serviceInfo")
    public ServiceInformation getServiceInfo()
    {
        return serviceInfo;
    }

    @JsonProperty("functions")
    public List<BasFunctionConfiguration> getFunctions()
    {
        return functions;
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
        BasServiceConfiguration that = (BasServiceConfiguration) o;
        return Objects.equals(name, that.name) && Objects.equals(serviceInfo, that.serviceInfo) && Objects.equals(functions, that.functions);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name, serviceInfo, functions);
    }
}

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
import com.bloomberg.datalake.trino.plugin.bas.BasConfig;
import com.google.inject.Inject;
import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ColumnMetadata;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.connector.SchemaTablePrefix;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class BasConfigClient
{
    private static final Logger log = Logger.get(BasConfigClient.class);

    private final List<BasServiceConfiguration> serviceConfigs;

    @Inject
    public BasConfigClient(BasConfig config, JsonCodec<List<BasServiceConfiguration>> codec)
    {
        requireNonNull(config, "config is null");
        requireNonNull(codec, "json codec is null");

        this.serviceConfigs = loadJsonFromFile(config.getMetadataFile(), codec);

        log.info("Loaded configuration for BAS connector: %s", this.serviceConfigs.toString());
    }

    public List<BasServiceConfiguration> getServiceConfigs()
    {
        return serviceConfigs;
    }

    public List<String> getServiceNames()
    {
        return serviceConfigs.stream().map(BasServiceConfiguration::getName).collect(Collectors.toUnmodifiableList());
    }

    public BasServiceConfiguration getService(String serviceName)
    {
        return serviceConfigs.stream().filter(s -> s.getName().equals(serviceName)).findFirst().orElseThrow();
    }

    public List<SchemaTableName> getFunctionNames(SchemaTablePrefix prefix)
    {
        if (prefix.isEmpty()) {
            return List.of();
        }

        if (prefix.getTable().isEmpty()) {
            return getFunctionNames(prefix.getSchema().get()).stream().map(n -> new SchemaTableName(prefix.getSchema().get(), n)).collect(Collectors.toUnmodifiableList());
        }

        return List.of(prefix.toSchemaTableName());
    }

    public List<String> getFunctionNames(String service)
    {
        return getFunctions(service).stream().map(BasFunctionConfiguration::getName).collect(Collectors.toUnmodifiableList());
    }

    public List<BasFunctionConfiguration> getFunctions(String service)
    {
        return serviceConfigs.stream().filter(s -> s.getName().equals(service)).findFirst().map(s -> s.getFunctions()).get();
    }

    public List<String> getFunctionNames()
    {
        return serviceConfigs.stream().flatMap(service -> service.getFunctions().stream().map(BasFunctionConfiguration::getName)).collect(Collectors.toUnmodifiableList());
    }

    public BasFunctionConfiguration getFunction(SchemaTableName stn)
    {
        return getFunction(stn.getSchemaName(), stn.getTableName());
    }

    public BasFunctionConfiguration getFunction(String service, String function)
    {
        return serviceConfigs.stream().filter(s -> s.getName().equals(service)).findFirst()
                .map(s -> s.getFunctions().stream().filter(f -> f.getName().equals(function)).findFirst())
                .get()
                .get();
    }

    public List<ColumnHandle> getColumnHandles(SchemaTableName stn)
    {
        return getColumnHandles(stn.getSchemaName(), stn.getTableName());
    }

    public List<ColumnHandle> getColumnHandles(String service, String function)
    {
        var parameterCols = getFunction(service, function).getParameterColumns().stream()
                .collect(Collectors.toUnmodifiableList());

        var cols = getFunction(service, function).getColumns().stream()
                .collect(Collectors.toUnmodifiableList());

        return Stream.concat(cols.stream(), parameterCols.stream()).collect(Collectors.toUnmodifiableList());
    }

    public List<ColumnMetadata> getColumnMetadata(SchemaTableName stn)
    {
        return getColumnMetadata(stn.getSchemaName(), stn.getTableName());
    }

    public List<ColumnMetadata> getColumnMetadata(String service, String function)
    {
        List<ColumnMetadata> parameterCols = getFunction(service, function).getParameterColumns().stream()
                .map(ch -> ch.getColumnMetadata(true))
                .collect(Collectors.toUnmodifiableList());

        List<ColumnMetadata> cols = getFunction(service, function).getColumns().stream()
                .map(BasColumnHandle::getColumnMetadata)
                .collect(Collectors.toUnmodifiableList());

        return Stream.concat(cols.stream(), parameterCols.stream()).collect(Collectors.toUnmodifiableList());
    }

    public boolean serviceExists(String name)
    {
        return serviceConfigs.stream().anyMatch(s -> s.getName().equals(name));
    }

    public boolean functionExists(SchemaTableName stn)
    {
        return functionExists(stn.getSchemaName(), stn.getTableName());
    }

    public boolean functionExists(String serviceName, String functionNaame)
    {
        return serviceConfigs.stream().filter(s -> s.getName().equals(serviceName)).findFirst()
                .map(s -> s.getFunctions().stream().anyMatch(f -> f.getName().equals(functionNaame)))
                .orElse(false);
    }

    /**
     * Creates a new list of services by loading the template of each function and replacing the filepath with
     * the contents of the file.
     * <p>
     * Template paths are relative to the location of the parent file.
     * <p>
     * Since configuration classes are immutable this generates new objects.
     */
    private List<BasServiceConfiguration> loadTemplates(List<BasServiceConfiguration> services, String rootPath)
    {
        return services.stream().map(s ->
                new BasServiceConfiguration(s.getName(), s.getServiceInfo(), s.getFunctions().stream().map(f ->
                {
                    try {
                        return new BasFunctionConfiguration(f.getName(), f.getColumns(), f.getParameterColumns(), Files.readString(Path.of(Path.of(rootPath).getParent().toString(), f.getRequestTemplate())), f.getResponseTransforms(), f.getBasErrorDetectors());
                    }
                    catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                        .collect(Collectors.toUnmodifiableList())))
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Loads a list of service configurations from a json file and for each function in those configs it loads its
     * template from it's respective file.
     */
    private List<BasServiceConfiguration> loadJsonFromFile(String filePath, JsonCodec<List<BasServiceConfiguration>> codec)
    {
        try {
            String fileContents = Files.readString(Path.of(filePath).toAbsolutePath().normalize());
            return loadTemplates(codec.fromJson(fileContents), filePath);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

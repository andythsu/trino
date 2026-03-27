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

import com.bloomberg.basreactor.external.basmessage.types.ServiceInformation;
import com.bloomberg.datalake.trino.plugin.bas.config.BasConfigClient;
import com.bloomberg.datalake.trino.plugin.bas.transforms.BasSpreadTransformAction;
import com.bloomberg.datalake.trino.plugin.bas.transforms.BasUnnestTransformAction;
import com.bloomberg.datalake.trino.plugin.bas.transforms.BasZipTransformAction;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import io.trino.spi.connector.ColumnMetadata;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.bloomberg.datalake.trino.plugin.bas.MetadataUtil.SERVICES_CODEC;
import static com.bloomberg.datalake.trino.plugin.bas.MetadataUtil.VARCHARARRAY;
import static io.trino.spi.type.VarcharType.createUnboundedVarcharType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestBasConfigClient
{
    private static final Map<String, BasColumnHandle> columnHandles = ImmutableMap.of(
            "securityName", new BasColumnHandle(createUnboundedVarcharType(), "securityName", "securityNameKey", Optional.empty(), Optional.empty()),
            "securityParameter", new BasColumnHandle(createUnboundedVarcharType(), "securityParameter", "securityNameParameterKey", Optional.empty(), Optional.empty()),
            "arrayCol", new BasColumnHandle(VARCHARARRAY, "arrayCol", "arrayColParameterKey", Optional.empty(), Optional.of(100)));

    private final BasConfigClient configClient;
    private final String template;

    TestBasConfigClient()
            throws IOException
    {
        BasConfig basConfig = new BasConfig().setMetadataFile(Resources.getResource("bas_configs/metadata.json").getPath());
        configClient = new BasConfigClient(basConfig, SERVICES_CODEC);
        template = Resources.toString(Resources.getResource("bas_configs/templates/svc.request.json.jinja2"), StandardCharsets.UTF_8);
    }

    @Test
    void testMetadataObjects()
    {
        assertThat(configClient.getServiceConfigs().size()).isEqualTo(1);

        var service = configClient.getServiceConfigs().get(0);
        assertThat(service.getName()).isEqualTo("svc");
        assertThat(service.getServiceInfo()).isEqualTo(ServiceInformation.parse("svc:1-1.11"));
        assertThat(service.getFunctions().size()).isEqualTo(1);

        var function = service.getFunctions().get(0);
        assertThat(function.getName()).isEqualTo("data_request");
        assertThat(function.getRequestTemplate()).isEqualTo(template);
        assertThat(function.getColumns()).isEqualTo(List.of(columnHandles.get("securityName")));
        assertThat(function.getParameterColumns()).isEqualTo(List.of(columnHandles.get("securityParameter"), columnHandles.get("arrayCol")));

        var actions = function.getResponseTransforms().getActions();
        var expectedActions = List.of(
                new BasUnnestTransformAction("value1"),
                new BasSpreadTransformAction("value2"),
                new BasZipTransformAction(List.of(
                        new BasZipTransformAction.SingleKeyZipper("value3"),
                        new BasZipTransformAction.ListZipper("value4", Pattern.compile("ab[0-9]+")))));
        assertThat(actions).isEqualTo(expectedActions);
    }

    @Test
    void testServiceInfo()
    {
        assertThat(configClient.getServiceConfigs().get(0).getServiceInfo()).isEqualTo(ServiceInformation.parse("svc:1-1.11"));
    }

    @Test
    void testGetServiceNames()
    {
        assertThat(configClient.getServiceNames()).isEqualTo(List.of("svc"));
    }

    @Test
    void testColumnHandles()
    {
        assertThat(configClient.getColumnHandles("svc", "data_request")).isEqualTo(columnHandles.values());
    }

    @Test
    void testColumnMetadata()
    {
        assertThat(configClient.getColumnMetadata("svc", "data_request")).isEqualTo(List.of(
                ColumnMetadata.builder().setName("securityName").setType(createUnboundedVarcharType()).setNullable(false).setExtraInfo(Optional.of("JSON key: securityNameKey")).build(),
                ColumnMetadata.builder().setName("securityParameter").setType(createUnboundedVarcharType()).setNullable(false).setExtraInfo(Optional.of("Parameter with template " +
                        "key: securityNameParameterKey")).build(),
                ColumnMetadata.builder().setName("arrayCol").setType(VARCHARARRAY).setNullable(false).setExtraInfo(Optional.of("Parameter with template key: " +
                        "arrayColParameterKey")).build()));
    }

    @Test
    void testFailureWhenMaxEntriesSetForNonArrayColumn()
    {
        assertThatThrownBy(() -> {
            new BasColumnHandle(createUnboundedVarcharType(), "nonArrayCol", "key", Optional.empty(), Optional.of(100));
        }).isInstanceOf(IllegalArgumentException.class);
    }
}

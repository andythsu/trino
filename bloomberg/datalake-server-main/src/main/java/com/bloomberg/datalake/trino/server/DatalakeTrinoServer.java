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
package com.bloomberg.datalake.trino.server;

import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import com.google.inject.Module;
import io.trino.server.Server;

import java.util.List;

import static com.google.common.base.Strings.nullToEmpty;
import static java.util.Objects.requireNonNullElse;

public class DatalakeTrinoServer
        extends Server
{
    private DatalakeTrinoServer() {}

    public static void main(String[] args)
    {
        String javaVersion = nullToEmpty(StandardSystemProperty.JAVA_VERSION.value());
        String majorVersion = javaVersion.split("[^\\d]", 2)[0];
        Integer major = Ints.tryParse(majorVersion);
        if (major == null || major < 22) {
            System.err.printf("ERROR: Trino requires Java 22+ (found %s)%n", javaVersion);
            System.exit(100);
        }

        String version = DatalakeTrinoServer.class.getPackage().getImplementationVersion();
        new DatalakeTrinoServer().start(requireNonNullElse(version, "unknown"));
    }

    @Override
    public List<Module> getAdditionalModules()
    {
        return ImmutableList.of(
                // Custom Bloomberg module
                new DatalakeModule());
    }
}

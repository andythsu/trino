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
package com.bloomberg.datalake.testcontainers;

import io.airlift.log.Logger;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.ImageNameSubstitutor;

public class DatalakeImageNameSubstitutor
        extends ImageNameSubstitutor
{
    private static final Logger log = Logger.get(DatalakeImageNameSubstitutor.class);

    @Override
    public DockerImageName apply(DockerImageName dockerImageName)
    {
        return switch (dockerImageName.getUnversionedPart()) {
            case "openpolicyagent/opa" -> DockerImageName.parse("artprod.dev.bloomberg.com/datalake/open-policy-agent/opa:v0.67.1");
            case "testcontainers/ryuk" -> DockerImageName.parse("artprod.dev.bloomberg.com/external-base/images/docker.io/testcontainers/ryuk:0.7.0");
            case "testcontainers/sshd" -> DockerImageName.parse("artprod.dev.bloomberg.com/external-base/images/docker.io/testcontainers/sshd:1.2.0");
            default -> dockerImageName;
        };
    }

    @Override
    protected String getDescription()
    {
        return this.getClass().getSimpleName();
    }
}

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
package com.bloomberg.datalake.trino;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static com.bloomberg.datalake.trino.DatalakeErrorMessageRecord.DatalakeErrorMessageBuilder;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestDatalakeErrorMessage
{
    private final String uuid = "12345";
    private final String message = "message";
    private final String userFriendlyMessage = "userFriendlyMessage";
    private final String remediationMessage = "remediationMessage";
    private final String query = "select 1";

    @Test
    public void testCanCreateErrorMessage()
    {
        DatalakeErrorMessage errorMessage = DatalakeErrorMessage.errorMessageBuilder()
                .setErrorMessage(message)
                .setUserFriendlyMessage(userFriendlyMessage)
                .setRemediationMessage(remediationMessage)
                .setQuery(query)
                .setUuid(uuid)
                .build();
        assertEquals(uuid, errorMessage.uuid());
        assertEquals(message, errorMessage.errorMessage());
        assertEquals(query, errorMessage.query());
        assertEquals(remediationMessage, errorMessage.remediationMessage());
        assertEquals(userFriendlyMessage, errorMessage.userFriendlyMessage());
    }

    @Test
    public void testUserFriendlyMessageAndMessageBothMissingShouldThrow()
    {
        DatalakeErrorMessageBuilder errorMessageBuilder = DatalakeErrorMessage.errorMessageBuilder()
                .setRemediationMessage(remediationMessage)
                .setQuery(query)
                .setUuid(uuid);
        assertThatThrownBy(errorMessageBuilder::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageMatching("userFriendlyMessage and message cannot be empty or missing at the same time");
    }

    @Test
    public void testUuidMissingShouldThrow()
    {
        DatalakeErrorMessageBuilder errorMessageBuilder = DatalakeErrorMessage.errorMessageBuilder()
                .setRemediationMessage(remediationMessage)
                .setQuery(query)
                .setErrorMessage(message)
                .setUserFriendlyMessage(userFriendlyMessage);
        assertThatThrownBy(errorMessageBuilder::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageMatching("uuid is missing");
    }
}

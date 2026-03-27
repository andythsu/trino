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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;

record DatalakeErrorMessageRecord(@LogAttribute("uuid") String uuid,
                                  @LogAttribute("session_id") String sessionId,
                                  @LogAttribute("user_friendly_message") String userFriendlyMessage,
                                  @LogAttribute("query") String query,
                                  @LogAttribute("remediation_message") String remediationMessage,
                                  @LogAttribute("error.message") String errorMessage,
                                  @LogAttribute("error.stack_trace") String errorStackTrace,
                                  @LogAttribute("error.type") String errorType,
                                  @LogAttribute("error.json") String errorJson,
                                  @LogAttribute("error.response") String errorResponse)
        implements DatalakeErrorMessage
{
    public static class DatalakeErrorMessageBuilder
    {
        private String uuid = "";
        private String sessionId = "";
        private String userFriendlyMessage = "";
        private String query = "";
        private String remediationMessage = "";
        private String errorMessage = "";
        private String errorStackTrace = "";
        private String errorType = "";
        private String errorJson = "";
        private String errorResponse = "";

        public DatalakeErrorMessageBuilder sessionId(String sessionId)
        {
            this.sessionId = sessionId;
            return this;
        }

        public DatalakeErrorMessageBuilder setErrorStackTrace(String errorStackTrace)
        {
            this.errorStackTrace = errorStackTrace;
            return this;
        }

        public DatalakeErrorMessageBuilder setErrorType(String errorType)
        {
            this.errorType = errorType;
            return this;
        }

        public DatalakeErrorMessageBuilder setErrorJson(String errorJson)
        {
            this.errorJson = errorJson;
            return this;
        }

        public DatalakeErrorMessageBuilder setErrorResponse(String errorResponse)
        {
            this.errorResponse = errorResponse;
            return this;
        }

        public DatalakeErrorMessageBuilder setErrorMessage(String errorMessage)
        {
            this.errorMessage = errorMessage;
            return this;
        }

        public DatalakeErrorMessageBuilder setUserFriendlyMessage(String userFriendlyMessage)
        {
            this.userFriendlyMessage = userFriendlyMessage;
            return this;
        }

        public DatalakeErrorMessageBuilder setUuid(String uuid)
        {
            this.uuid = uuid;
            return this;
        }

        public DatalakeErrorMessageBuilder setQuery(String query)
        {
            this.query = query;
            return this;
        }

        public DatalakeErrorMessageBuilder setRemediationMessage(String remediationMessage)
        {
            this.remediationMessage = remediationMessage;
            return this;
        }

        public DatalakeErrorMessage build()
        {
            checkState(!isNullOrEmpty(uuid), "uuid is missing");
            boolean isUserFriendlyMessageMissing = isNullOrEmpty(userFriendlyMessage);
            boolean isErrorMessageMissing = isNullOrEmpty(errorMessage);
            if (isUserFriendlyMessageMissing && isErrorMessageMissing) {
                throw new IllegalStateException("userFriendlyMessage and message cannot be empty or missing at the same time");
            }
            String userFriendlyMsg = isUserFriendlyMessageMissing ? errorMessage : userFriendlyMessage;
            String errorMsg = isErrorMessageMissing ? userFriendlyMessage : errorMessage;
            return new DatalakeErrorMessageRecord(
                    uuid,
                    sessionId,
                    userFriendlyMsg,
                    query,
                    remediationMessage,
                    errorMsg,
                    errorStackTrace,
                    errorType,
                    errorJson,
                    errorResponse);
        }
    }
}

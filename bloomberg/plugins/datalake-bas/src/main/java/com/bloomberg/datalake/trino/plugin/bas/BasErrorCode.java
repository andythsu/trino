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

import io.trino.spi.ErrorCode;
import io.trino.spi.ErrorCodeSupplier;
import io.trino.spi.ErrorType;

import static io.trino.spi.ErrorType.EXTERNAL;
import static io.trino.spi.ErrorType.INTERNAL_ERROR;
import static io.trino.spi.ErrorType.USER_ERROR;

public enum BasErrorCode
        implements ErrorCodeSupplier
{
    BAS_PARAMETER_NOT_PROVIDED(0, USER_ERROR),
    BAS_PARAMETER_BAD_VALUE(1, USER_ERROR),
    BAS_PARAMETER_UNKNOWN_TYPE(2, INTERNAL_ERROR),
    BAS_TEMPLATING_ERROR(3, INTERNAL_ERROR),
    BAS_CLIENT_ERROR(4, EXTERNAL),
    BAS_RESPONSE_BAD_VALUE(5, EXTERNAL),
    /**/;

    private final ErrorCode errorCode;

    BasErrorCode(int code, ErrorType type)
    {
        errorCode = new ErrorCode(code + 0x0606_0000, name(), type);
    }

    @Override
    public ErrorCode toErrorCode()
    {
        return errorCode;
    }
}

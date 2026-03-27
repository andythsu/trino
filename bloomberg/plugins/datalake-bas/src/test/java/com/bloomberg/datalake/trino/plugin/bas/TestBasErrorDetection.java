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

import com.bloomberg.datalake.trino.plugin.bas.errordetection.BasErrorDetector;
import com.bloomberg.datalake.trino.plugin.bas.errordetection.BasErrorDetectorIfKeyExists;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestBasErrorDetection
{
    @Test
    void testErrorDetection()
    {
        assertErrorDetection(
                ImmutableList.of(new BasErrorDetectorIfKeyExists(ImmutableList.of("CalcrtResponse", "error"))),
                ImmutableMap.of("CalcrtResponse", ImmutableMap.of("error", ImmutableMap.of("foo", "bar"))),
                true);
        assertErrorDetection(
                ImmutableList.of(new BasErrorDetectorIfKeyExists(ImmutableList.of("CalcrtResponse", "error"))),
                ImmutableMap.of("Mismatch", ImmutableMap.of("error", ImmutableMap.of("foo", "bar"))),
                false);
        assertErrorDetection(
                ImmutableList.of(new BasErrorDetectorIfKeyExists(ImmutableList.of("CalcrtResponse", "error"))),
                ImmutableMap.of("CalcrtResponse", "not-a-ImmutableMap"),
                false);
        assertErrorDetection(
                ImmutableList.of(
                        new BasErrorDetectorIfKeyExists(ImmutableList.of("errorResponse")),
                        new BasErrorDetectorIfKeyExists(ImmutableList.of("errorInfo"))),
                ImmutableMap.of("errorResponse", "some-error"),
                true);
        assertErrorDetection(
                ImmutableList.of(
                        new BasErrorDetectorIfKeyExists(ImmutableList.of("errorResponse")),
                        new BasErrorDetectorIfKeyExists(ImmutableList.of("errorInfo"))),
                ImmutableMap.of("errorInfo", "some-error"),
                true);
        assertErrorDetection(
                ImmutableList.of(
                        new BasErrorDetectorIfKeyExists(ImmutableList.of("errorResponse")),
                        new BasErrorDetectorIfKeyExists(ImmutableList.of("errorInfo"))),
                ImmutableMap.of("errorInfo", "some-error", "errorResponse", "some-error"),
                true);
        assertErrorDetection(
                ImmutableList.of(
                        new BasErrorDetectorIfKeyExists(ImmutableList.of("errorResponse")),
                        new BasErrorDetectorIfKeyExists(ImmutableList.of("errorInfo"))),
                ImmutableMap.of("mismatch1", "some-error", "mismatch2", ImmutableMap.of("error", ImmutableMap.of("foo", "bar"))),
                false);
        assertErrorDetection(
                ImmutableList.of(new BasErrorDetectorIfKeyExists(ImmutableList.of("foo", "bar", "baz"))),
                ImmutableMap.of("foo", ImmutableMap.of("bar", ImmutableMap.of("xyz", "abc", "baz", ImmutableMap.of("random", 1)))),
                true);
        assertErrorDetection(
                ImmutableList.of(new BasErrorDetectorIfKeyExists(ImmutableList.of("foo", "bar", "baz"))),
                ImmutableMap.of("foo", ImmutableMap.of("bar", ImmutableMap.of("xyz", "abc", "notquitebaz", ImmutableMap.of("random", 1)))),
                false);
    }

    private static void assertErrorDetection(ImmutableList<BasErrorDetector> errorDetectors, ImmutableMap<String, Object> response, boolean expected)
    {
        assertThat(BasErrorDetector.from(errorDetectors).test(response)).isEqualTo(expected);
    }
}

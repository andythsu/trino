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
package com.bloomberg.datalake.trino.plugin.bas.errordetection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Detects an error by checking if {@code key} is present in the {@code basResponse}.
 */
public record BasErrorDetectorIfKeyExists(List<String> key)
        implements BasErrorDetector
{
    /**
     * A list of strings denoting the path of the key the detector is searching for in the BAS response.
     */
    @JsonProperty("key")
    public List<String> key()
    {
        return key;
    }

    @JsonCreator
    public BasErrorDetectorIfKeyExists(
            @JsonProperty("key") List<String> key)
    {
        if (key == null || key.size() == 0) {
            throw new IllegalArgumentException("key is empty");
        }
        this.key = key;
    }

    /**
     * Detects whether {@code this.key} is present in {@code partialResponse} starting from {@code keyIndex}.
     *
     * @param partialResponse A slice of the {@code basResponse}.
     * @param keyIndex The index of the key from {@code this.key} being considered in this run.
     * @return {@code true} if the key exists, {@code false} otherwise.
     */
    private boolean detectRecursive(Map<String, Object> partialResponse, int keyIndex)
    {
        // Just in case someone passes a bad keyIndex to the top-level call.
        if (keyIndex > key.size() - 1) {
            return false;
        }

        String currentKey = key.get(keyIndex);
        boolean doesKeyExist = partialResponse.containsKey(currentKey);

        if (keyIndex == (key.size() - 1)) {
            return doesKeyExist;
        }

        if (!doesKeyExist || !(partialResponse.get(currentKey) instanceof Map)) {
            return false;
        }

        Map<String, Object> newPartialResponse = (Map<String, Object>) partialResponse.get(currentKey);

        return detectRecursive(newPartialResponse, keyIndex + 1);
    }

    /**
     * Detects whether {@code this.key} is present in {@code basResponse}.
     *
     * @param basResponse a JSON response from BAS in which the existence of {@code this.key} is to be checked.
     * @return {@code true} if the key exists, {@code false} otherwise.
     */
    @Override
    public boolean test(Map<String, Object> basResponse)
    {
        return detectRecursive(basResponse, 0);
    }
}

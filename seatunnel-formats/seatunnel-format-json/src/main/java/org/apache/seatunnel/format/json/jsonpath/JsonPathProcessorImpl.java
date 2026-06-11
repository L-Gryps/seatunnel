/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.format.json.jsonpath;

import org.apache.seatunnel.common.utils.JsonUtils;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;

import java.util.ArrayList;
import java.util.List;

/** Default implementation of JsonPathProcessor providing common functionality. */
public class JsonPathProcessorImpl implements JsonPathProcessor {

    /** Flag to indicate whether to return null for missing fields */
    private boolean jsonFieldMissedReturnNull = false;

    /**
     * Set whether to return null for missing fields.
     *
     * @param jsonFieldMissedReturnNull true to return null for missing fields, false otherwise
     */
    public void setJsonFieldMissedReturnNull(boolean jsonFieldMissedReturnNull) {
        this.jsonFieldMissedReturnNull = jsonFieldMissedReturnNull;
    }

    /**
     * Check if json fields with missing values should return null. This is used to determine
     * whether to validate result consistency.
     *
     * @return true if missing fields should return null, false otherwise
     */
    protected boolean isJsonFieldMissedReturnNull() {
        return jsonFieldMissedReturnNull;
    }

    /** {@inheritDoc} */
    @Override
    public List<List<String>> processJsonData(ReadContext jsonReadContext, JsonPath[] paths) {
        List<List<?>> results = new ArrayList<>(paths.length);

        for (JsonPath path : paths) {
            results.add(jsonReadContext.read(path));
        }

        boolean shouldValidate = !isJsonFieldMissedReturnNull();
        if (shouldValidate) {
            validateResultsConsistency(results, paths);
        }

        return dataFlip(results);
    }

    /**
     * Helper method to validate that all array paths (paths returning multiple records) have the
     * same size. Scalar paths (returning 0 or 1 record) are allowed to differ and will be
     * broadcast.
     *
     * @param results The list of results to validate
     * @param paths The JsonPath objects used to generate the results
     */
    protected void validateResultsConsistency(List<? extends List<?>> results, JsonPath[] paths) {
        if (results.isEmpty()) {
            return;
        }

        Integer arraySize = null;
        Integer arrayPathIndex = null;
        for (int i = 0; i < results.size(); i++) {
            List<?> list = results.get(i);
            int size = list.size();
            if (size <= 1) {
                continue;
            }
            if (arraySize == null) {
                arraySize = size;
                arrayPathIndex = i;
            } else if (size != arraySize) {
                throw new IllegalArgumentException(
                        String.format(
                                "[%s](%d) and [%s](%d) the number of parsing records is inconsistent.",
                                paths[arrayPathIndex].getPath(),
                                arraySize,
                                paths[i].getPath(),
                                size));
            }
        }
    }

    /**
     * Flips a matrix of results so that rows become columns and vice versa. Scalar paths returning
     * fewer records than the maximum array size will have their value broadcast across all rows.
     *
     * @param results The original data matrix
     * @return The flipped data matrix
     */
    protected List<List<String>> dataFlip(List<? extends List<?>> results) {
        int maxSize = 0;
        for (List<?> result : results) {
            maxSize = Math.max(maxSize, result.size());
        }

        List<List<String>> datas = new ArrayList<>();
        for (int i = 0; i < maxSize; i++) {
            datas.add(new ArrayList<>(results.size()));
        }

        for (List<?> result : results) {
            int resultSize = result.size();
            for (int i = 0; i < maxSize; i++) {
                int index = i < resultSize ? i : resultSize - 1;
                Object val = resultSize == 0 ? null : result.get(index);
                datas.get(i).add(val == null ? null : val.toString());
            }
        }

        return datas;
    }

    /**
     * Extract value from a JSON context using a relative path.
     *
     * @param objContext The JSON read context
     * @param relativePath The relative path to extract from
     * @return The extracted value as a string
     */
    protected String extractValue(ReadContext objContext, String relativePath) {
        try {
            Object value = objContext.read(relativePath);
            if (value == null) {
                return null;
            }
            if (value instanceof String) {
                // For string types, return the original value directly without JSON serialization,
                // otherwise "value" will become "\"value\""
                return (String) value;
            }
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                return !list.isEmpty() ? JsonUtils.toJsonString(list) : null;
            }
            // For other non-string values, use JsonUtils to serialize them.
            return JsonUtils.toJsonString(value);
        } catch (Exception e) {
            return null;
        }
    }
}

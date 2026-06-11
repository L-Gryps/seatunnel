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

import org.apache.seatunnel.api.source.Collector;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.common.utils.JsonUtils;
import org.apache.seatunnel.format.json.JsonDeserializationSchema;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ReadContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonPathDeserializationSchema extends JsonDeserializationSchema {

    private final Map<String, String> jsonField;
    private transient JsonPath[] jsonPaths;
    private transient Configuration jsonConfiguration;

    private Configuration getJsonConfiguration() {
        if (jsonConfiguration == null) {
            jsonConfiguration =
                    Configuration.defaultConfiguration()
                            .addOptions(
                                    Option.SUPPRESS_EXCEPTIONS,
                                    Option.ALWAYS_RETURN_LIST,
                                    Option.DEFAULT_PATH_LEAF_TO_NULL);
        }
        return jsonConfiguration;
    }

    public JsonPathDeserializationSchema(
            boolean failOnMissingField,
            boolean ignoreParseErrors,
            SeaTunnelRowType rowType,
            Map<String, String> jsonField) {
        super(failOnMissingField, ignoreParseErrors, rowType);
        this.jsonField = jsonField;
    }

    public JsonPathDeserializationSchema(
            CatalogTable catalogTable,
            boolean failOnMissingField,
            boolean ignoreParseErrors,
            Map<String, String> jsonField) {
        super(catalogTable, failOnMissingField, ignoreParseErrors);
        this.jsonField = new LinkedHashMap<>(jsonField);
    }

    /**
     * Initialize JsonPath array lazily and cache it.
     */
    private void initJsonPath() {
        if (jsonPaths == null) {
            jsonPaths = createJsonPaths(jsonField);
        }
    }

    @Override
    public SeaTunnelRow deserialize(byte[] message) throws IOException {
        if (jsonField == null) {
            return null;
        }
        initJsonPath();
        List<List<String>> decoded = decodeJson(new String(message, StandardCharsets.UTF_8));
        if (decoded == null || decoded.isEmpty()) {
            return null;
        }
        List<Map<String, String>> mapped = parseToMap(decoded, jsonField);
        String contentData = JsonUtils.toJsonNode(mapped.get(0)).toString();
        return super.deserialize(contentData.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void collect(byte[] message, Collector<SeaTunnelRow> out) throws IOException {
        if (jsonField == null) {
            return;
        }
        initJsonPath();
        List<List<String>> decoded = decodeJson(new String(message, StandardCharsets.UTF_8));
        if (decoded == null || decoded.isEmpty()) {
            return;
        }
        List<Map<String, String>> mapped = parseToMap(decoded, jsonField);
        for (Map<String, String> row : mapped) {
            String projectedJson = JsonUtils.toJsonNode(row).toString();
            super.collect(projectedJson.getBytes(StandardCharsets.UTF_8), out);
        }
    }

    @Override
    public void deserialize(byte[] message, Collector<SeaTunnelRow> out) throws IOException {
        this.collect(message, out);
    }

    private List<List<String>> decodeJson(String data) {
        Configuration jsonConfiguration = getJsonConfiguration();
        ReadContext jsonReadContext = JsonPath.using(jsonConfiguration).parse(data);
        return JsonPathProcessorFactory.getProcessor(this.jsonPaths, false)
                .processJsonData(jsonReadContext, this.jsonPaths);
    }

    public static JsonPath[] createJsonPaths(Map<String, String> jsonField) {
        if (jsonField == null || jsonField.isEmpty()) {
            throw new IllegalArgumentException("JsonField cannot be null or empty");
        }

        JsonPath[] jsonPaths = new JsonPath[jsonField.size()];
        int index = 0;
        for (String pathString : jsonField.values()) {
            jsonPaths[index++] = JsonPath.compile(pathString);
        }

        return jsonPaths;
    }

    public static List<Map<String, String>> parseToMap(
            List<List<String>> data, Map<String, String> jsonField) {
        List<Map<String, String>> resultList = new ArrayList<>(data.size());
        String[] keys = jsonField.keySet().toArray(new String[0]);

        for (List<String> row : data) {
            Map<String, String> resultMap = new HashMap<>(jsonField.size());
            for (int i = 0; i < row.size(); i++) {
                resultMap.put(keys[i], row.get(i));
            }
            resultList.add(resultMap);
        }

        return resultList;
    }
}

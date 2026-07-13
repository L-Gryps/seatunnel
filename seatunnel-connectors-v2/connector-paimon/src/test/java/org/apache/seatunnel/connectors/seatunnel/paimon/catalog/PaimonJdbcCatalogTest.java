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

package org.apache.seatunnel.connectors.seatunnel.paimon.catalog;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.common.utils.SeaTunnelException;
import org.apache.seatunnel.connectors.seatunnel.paimon.config.PaimonConfig;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class PaimonJdbcCatalogTest {

    private static final String WAREHOUSE = "file:///tmp/paimon";
    private static final String JDBC_URI = "jdbc:mysql://mysql-host:3306/paimon_metastore";
    private static final String JDBC_USER = "root";
    private static final String JDBC_PASSWORD = "123456";

    /** When catalog_type is jdbc, catalog_uri is required. */
    @Test
    public void jdbcCatalogUriRequired() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("warehouse", WAREHOUSE);
        properties.put("catalog_type", "jdbc");
        properties.put("database", "default");
        properties.put("table", "test_table");
        ReadonlyConfig config = ReadonlyConfig.fromMap(properties);
        SeaTunnelException e =
                Assertions.assertThrows(SeaTunnelException.class, () -> new PaimonConfig(config));
        Assertions.assertTrue(e.getMessage().contains("catalog_uri"));
    }

    /** When catalog_type is jdbc and catalog_uri is provided, the config is built successfully. */
    @Test
    public void jdbcCatalogConfigBuilt() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("warehouse", WAREHOUSE);
        properties.put("catalog_type", "jdbc");
        properties.put("catalog_uri", JDBC_URI);
        properties.put("jdbc.user", JDBC_USER);
        properties.put("jdbc.password", JDBC_PASSWORD);
        properties.put("database", "default");
        properties.put("table", "test_table");
        ReadonlyConfig config = ReadonlyConfig.fromMap(properties);

        PaimonConfig paimonConfig = new PaimonConfig(config);
        Assertions.assertEquals(PaimonCatalogEnum.JDBC, paimonConfig.getCatalogType());
        Assertions.assertEquals(JDBC_URI, paimonConfig.getCatalogUri());
        Assertions.assertEquals(JDBC_USER, paimonConfig.getJdbcUser());
        Assertions.assertEquals(JDBC_PASSWORD, paimonConfig.getJdbcPassword());
    }

    /**
     * The jdbc catalog loader should assemble metastore, uri and jdbc credentials into the paimon
     * options. We verify this by building the loader and checking it does not throw, the real
     * connection is not established here because it requires a live database and a jdbc driver on
     * the classpath.
     */
    @Test
    public void jdbcCatalogLoaderBuildable() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("warehouse", WAREHOUSE);
        properties.put("catalog_type", "jdbc");
        properties.put("catalog_uri", JDBC_URI);
        properties.put("jdbc.user", JDBC_USER);
        properties.put("jdbc.password", JDBC_PASSWORD);
        properties.put("database", "default");
        properties.put("table", "test_table");
        ReadonlyConfig config = ReadonlyConfig.fromMap(properties);

        PaimonConfig paimonConfig = new PaimonConfig(config);
        // Building the loader must not throw; actual catalog creation needs a live jdbc metastore.
        PaimonCatalogLoader loader = new PaimonCatalogLoader(paimonConfig);
        Assertions.assertNotNull(loader);
    }
}

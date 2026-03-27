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

package com.bloomberg.datalake.trino.plugin.geotools;

import com.google.common.collect.ImmutableList;
import io.trino.plugin.geospatial.GeoPlugin;
import io.trino.spi.type.ArrayType;
import io.trino.sql.query.QueryAssertions;
import io.trino.testing.QueryFailedException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class H3FunctionTest
{
    private QueryAssertions assertions;

    @BeforeAll
    public void init()
    {
        assertions = new QueryAssertions();
        assertions.addPlugin(new GeoPlugin());
        assertions.addPlugin(new GeoToolsPlugin());
    }

    @AfterAll
    public void teardown()
    {
        assertions.close();
        assertions = null;
    }

    @Test
    public void testLatLongToCell()
    {
        assertThat(assertions.function(
                "h3_point_geometry_to_cell_bigint",
                "ST_GeometryFromText('POINT(-73.9857 40.7484)')",
                "4"))
                .hasType(BIGINT)
                .isEqualTo(595215130728333311L);
        assertThat(assertions.function(
                "h3_lat_long_to_cell_bigint",
                "40.7484",
                "-73.9857",
                "4"))
                .hasType(BIGINT)
                .isEqualTo(595215130728333311L);

        assertThat(assertions.function(
                "h3_lat_long_to_cell_hex",
                "40.7484", "-73.9857", "4"))
                .hasType(VARCHAR)
                .isEqualTo("842a101ffffffff");

        assertThat(assertions.function(
                "h3_point_geometry_to_cell_hex",
                "ST_GeometryFromText('POINT(-73.9857 40.7484)')",
                "4"))
                .hasType(VARCHAR)
                .isEqualTo("842a101ffffffff");

        assertThatThrownBy(() ->
                assertions.function(
                        "h3_point_geometry_to_cell_bigint",
                        "ST_GeometryFromText('POINT(-73.9857 40.7484)')",
                        "-1").evaluate()
        ).isInstanceOf(QueryFailedException.class)
                .hasMessageContaining("h3_lat_long_to_cell: resolution must be between 0 and 15");

        assertThatThrownBy(() ->
                assertions.function(
                        "h3_lat_long_to_cell_bigint",
                        "40.7484", "-73.9857",
                        "-1").evaluate()
        ).isInstanceOf(QueryFailedException.class)
                .hasMessageContaining("h3_lat_long_to_cell: resolution must be between 0 and 15");

        assertThatThrownBy(() ->
                assertions.function(
                        "h3_point_geometry_to_cell_hex",
                        "ST_GeometryFromText('POINT(-73.9857 40.7484)')",
                        "-1").evaluate()
        ).isInstanceOf(QueryFailedException.class)
                .hasMessageContaining("h3_lat_long_to_cell: resolution must be between 0 and 15");

        assertThatThrownBy(() ->
                assertions.function(
                        "h3_lat_long_to_cell_hex",
                        "40.7484", "-73.9857",
                        "-1").evaluate()
        ).isInstanceOf(QueryFailedException.class)
                .hasMessageContaining("h3_lat_long_to_cell: resolution must be between 0 and 15");

        assertThatThrownBy(() ->
                assertions.function(
                        "h3_point_geometry_to_cell_bigint",
                        "ST_GeometryFromText('LINESTRING (0 0, 1 1)')",
                        "1").evaluate()
        ).isInstanceOf(QueryFailedException.class)
                .hasMessageContaining("h3_latlng_to_cell: input geometry must be a POINT");

        assertThatThrownBy(() ->
                assertions.function(
                        "h3_point_geometry_to_cell_hex",
                        "ST_GeometryFromText('LINESTRING (0 0, 1 1)')",
                        "1").evaluate()
        ).isInstanceOf(QueryFailedException.class)
                .hasMessageContaining("h3_latlng_to_cell: input geometry must be a POINT");

        assertThatThrownBy(() ->
                assertions.function(
                        "h3_point_geometry_to_cell_bigint",
                        "ST_Point(190.0, 0.0)",
                        "1").evaluate()
        ).isInstanceOf(QueryFailedException.class)
                .hasMessageContaining("h3_latlng_to_cell: Lat-long out of range");

        assertThatThrownBy(() ->
                assertions.function(
                        "h3_point_geometry_to_cell_hex",
                        "ST_Point(190.0, 0.0)",
                        "1").evaluate()
        ).isInstanceOf(QueryFailedException.class)
                .hasMessageContaining("h3_latlng_to_cell: Lat-long out of range");

        assertThatThrownBy(() ->
                assertions.function(
                        "h3_lat_long_to_cell_hex",
                        "190.0", "0.0",
                        "1").evaluate()
        ).isInstanceOf(QueryFailedException.class)
                .hasMessageContaining("h3_latlng_to_cell: Lat-long out of range");

        assertThatThrownBy(() ->
                assertions.function(
                        "h3_lat_long_to_cell_bigint",
                        "190.0", "0.0",
                        "1").evaluate()
        ).isInstanceOf(QueryFailedException.class)
                .hasMessageContaining("h3_latlng_to_cell: Lat-long out of range");
    }

    @Test
    public void testPolygonToCell()
    {
        assertThat(assertions.function(
                "h3_polygon_to_cells",
                "ST_GeometryFromText('POLYGON((0 0, 1 1, 1 0, 0 0))')",
                "4"))
                .hasType(new ArrayType(BIGINT))
                .isEqualTo(ImmutableList.of(
                        596538848238895103L,
                        596538813879156735L,
                        596538685030137855L,
                        596538693620072447L));

        // test for polygon with holes
        assertThat(assertions.function(
                "h3_polygon_to_cells",
                """
                ST_GeometryFromText('POLYGON((
                    40.21164348202902 -75.29775099880715,
                    40.15848824273928 -75.20622543735006,
                    40.216835543332536 -75.10342967508292,
                    40.28435832913473 -75.13211508849594,
                    40.297006777417096 -75.27737167414664,
                    40.21164348202902 -75.29775099880715
                ),(
                    40.25895426095832 -75.24108343669435,
                    40.20644491592128 -75.24106784123632,
                    40.21740167859974 -75.18055471637605,
                    40.235871134451486 -75.1692108285083,
                    40.252029492728326 -75.16620352338438,
                    40.25895426095832 -75.24108343669435
                ))')
                """,
                "6"))
                .hasType(new ArrayType(BIGINT))
                .isEqualTo(ImmutableList.of(607710827511807999L));

        assertThatThrownBy(() ->
                assertions.function(
                        "h3_polygon_to_cells",
                        "ST_GeometryFromText('POLYGON((0 0, 1 1, 1 0, 0 0))')",
                        "-1").evaluate()
        ).isInstanceOf(QueryFailedException.class)
                .hasMessageContaining("h3_polygon_to_cell: resolution must be between 0 and 15");

        assertThatThrownBy(() ->
                assertions.function(
                        "h3_polygon_to_cells",
                        "ST_GeometryFromText('LINESTRING (0 0, 1 1)')",
                        "1").evaluate()
        ).isInstanceOf(QueryFailedException.class)
                .hasMessageContaining("h3_polygon_to_cell: Invalid polygon geometry");
    }

    @Test
    public void getResolution()
    {
        assertThat(assertions.function(
                "h3_get_resolution",
                "from_base('85283473fffffff', 16)"))
                .hasType(INTEGER)
                .isEqualTo(5);
    }

    @Test
    public void isValidCell()
    {
        assertThat(assertions.function(
                "h3_is_valid_cell",
                "from_base('85283473fffffff', 16)"))
                .hasType(BOOLEAN)
                .isEqualTo(true);

        assertThat(assertions.function(
                "h3_is_valid_cell",
                "0"))
                .hasType(BOOLEAN)
                .isEqualTo(false);
    }

    @Test
    public void testCellToChildren()
    {
        assertThat(assertions.function(
                "h3_cell_to_children",
                "from_base('85283473fffffff', 16)",
                "6"))
                .hasType(new ArrayType(BIGINT))
                .isEqualTo(ImmutableList.of(
                        604189641121202175L,
                        604189641255419903L,
                        604189641389637631L,
                        604189641523855359L,
                        604189641658073087L,
                        604189641792290815L,
                        604189641926508543L));

        assertThatThrownBy(() ->
                assertions.function(
                        "h3_cell_to_children",
                        "from_base('85283473fffffff', 16)",
                        "-1").evaluate()
        ).isInstanceOf(QueryFailedException.class)
                .hasMessageContaining("h3_cells_to_children: resolution must be between 0 and 15");
    }

    @Test
    public void testGetRes0Cells()
    {
        assertThat(assertions.function("cardinality", "h3_get_res0_cells()"))
                .hasType(BIGINT)
                .isEqualTo(122L);
    }
}

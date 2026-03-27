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

import io.airlift.slice.Slice;
import io.trino.spi.TrinoException;
import io.trino.spi.function.Description;
import io.trino.spi.function.ScalarFunction;
import io.trino.spi.function.SqlNullable;
import io.trino.spi.function.SqlType;
import io.trino.spi.type.StandardTypes;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;

import static io.trino.geospatial.serde.JtsGeometrySerde.deserialize;
import static io.trino.geospatial.serde.JtsGeometrySerde.serialize;
import static io.trino.spi.StandardErrorCode.INVALID_FUNCTION_ARGUMENT;
import static io.trino.spi.type.StandardTypes.DOUBLE;
import static java.lang.Double.isNaN;

public class BufferFunction
{
    /**
     * The Source CRS definition (EPSG:4326).
     */
    private static final CoordinateReferenceSystem SOURCE_CRS;
    /**
     * The Target CRS definition (EPSG:3857).
     */
    private static final CoordinateReferenceSystem TARGET_CRS;
    /**
     * Transformation function definition SOURCE_CRS -> TARGET_CRS.
     */
    private static final MathTransform TRANSFORM;
    /**
     * Transformation inverse function definition TARGET_CRS -> TARGET_CRS.
     */
    private static final MathTransform TRANSFORM_INVERSE;

    /*
     * We statically define the WKT of the two projection systems: EPSG:4326 and
     * EPSG:3857. Since we know that we are only projecting between these two
     * systems, we can define them here and create the math transform functions
     * so that it is initialized once. They will not be modified by any of the
     * following functions.
     */
    static {
        try {
            SOURCE_CRS = CRS.parseWKT("""
                    GEOGCS["WGS 84",
                        DATUM["WGS_1984",
                            SPHEROID["WGS 84",6378137,298.257223563,
                                AUTHORITY["EPSG","7030"]],
                            AUTHORITY["EPSG","6326"]],
                        PRIMEM["Greenwich",0,
                            AUTHORITY["EPSG","8901"]],
                        UNIT["degree",0.0174532925199433,
                            AUTHORITY["EPSG","9122"]],
                        AXIS["Longitude",EAST],
                        AXIS["Latitude",NORTH],
                        AUTHORITY["BBGEO","4326"]]
                    """);
            TARGET_CRS = CRS.parseWKT("""
                    PROJCS["WGS 84 / Pseudo-Mercator",
                        GEOGCS["WGS 84",
                            DATUM["WGS_1984",
                                SPHEROID["WGS 84",6378137,298.257223563,
                                    AUTHORITY["EPSG","7030"]],
                                AUTHORITY["EPSG","6326"]],
                            PRIMEM["Greenwich",0,
                                AUTHORITY["EPSG","8901"]],
                            UNIT["degree",0.0174532925199433,
                                AUTHORITY["EPSG","9122"]],
                            AUTHORITY["EPSG","4326"]],
                        PROJECTION["Mercator_1SP"],
                        PARAMETER["central_meridian",0],
                        PARAMETER["scale_factor",1],
                        PARAMETER["false_easting",0],
                        PARAMETER["false_northing",0],
                        UNIT["metre",1,
                            AUTHORITY["EPSG","9001"]],
                        AXIS["Easting",EAST],
                        AXIS["Northing",NORTH],
                        AUTHORITY["EPSG","3857"]]
                    """);
            TRANSFORM = CRS.findMathTransform(SOURCE_CRS, TARGET_CRS);
            TRANSFORM_INVERSE = CRS.findMathTransform(TARGET_CRS, SOURCE_CRS);
        }
        catch (FactoryException e) {
            throw new RuntimeException(e);
        }
    }

    private BufferFunction()
    {
        // not called
    }

    /**
     * Function that buffers a Geometry by a distance in meters.
     * It expects that the input geometry is of the CRS EPSG:4326. It
     * transforms this to EPSG:3857, performs a buffer based on `distance`,
     * and then transforms the buffered geometry back to EPSG:4326.
     *
     * @param geom input Geometry provided by the user.
     * @param distance amount to buffer original geometry in meters.
     * @return the buffered geometry in EPSG:4326 reference system
     */
    private static Geometry bufferGeometry(final Geometry geom,
            final double distance)
    {
        Geometry geometry3857;
        Geometry bufferedGeometry;
        Geometry finalGeometry;
        try {
            geometry3857 = JTS.transform(geom, TRANSFORM);
            bufferedGeometry = geometry3857.buffer(distance);
            finalGeometry = JTS.transform(bufferedGeometry, TRANSFORM_INVERSE);
        }
        catch (TransformException e) {
            throw new TrinoException(INVALID_FUNCTION_ARGUMENT, e.getMessage());
        }
        return finalGeometry;
    }

    /**
     * Trino Function registration that defines ST_Buffer4326to3857.
     *
     * @param input the input Geometry.
     * @param distance the distance to buffer in meters.
     * @return a new Geometry slice of that Old Geometry buffered.
     */
    @ScalarFunction("ST_Buffer4326to3857")
    @Description("Buffers EPSG:4326 geom by a distance (in m) by converting to "
            + "EPSG:3857.")
    @SqlNullable
    @SqlType(StandardTypes.GEOMETRY)
    public static Slice stBuffer4326To3857(
            @SqlType(StandardTypes.GEOMETRY) Slice input,
            @SqlType(DOUBLE) double distance)
    {
        if (isNaN(distance)) {
            throw new TrinoException(INVALID_FUNCTION_ARGUMENT,
                    "distance is NaN");
        }
        if (distance < 0) {
            throw new TrinoException(INVALID_FUNCTION_ARGUMENT,
                    "distance is negative");
        }
        if (distance == 0) {
            return input;
        }

        Geometry geometry = deserialize(input);
        if (geometry.isEmpty()) {
            return null;
        }

        return serialize(bufferGeometry(geometry, distance));
    }
}

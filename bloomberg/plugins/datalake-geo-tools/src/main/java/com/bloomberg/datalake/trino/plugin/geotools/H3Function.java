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
import com.uber.h3core.util.LatLng;
import io.airlift.log.Logger;
import io.airlift.slice.Slice;
import io.airlift.slice.Slices;
import io.trino.spi.TrinoException;
import io.trino.spi.block.Block;
import io.trino.spi.function.Description;
import io.trino.spi.function.ScalarFunction;
import io.trino.spi.function.SqlNullable;
import io.trino.spi.function.SqlType;
import io.trino.spi.type.StandardTypes;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.trino.geospatial.serde.JtsGeometrySerde.deserialize;
import static io.trino.spi.StandardErrorCode.GENERIC_INTERNAL_ERROR;
import static java.lang.Math.toIntExact;
import static org.locationtech.jts.geom.Geometry.TYPENAME_POINT;
import static org.locationtech.jts.geom.Geometry.TYPENAME_POLYGON;

public class H3Function
{
    private static final Logger log = Logger.get(H3Function.class);
    private static final int MAX_H3_RESOLUTION = 15;

    private H3Function()
    {
        // not used
    }

    static List<LatLng> linearRingTolatLngList(LinearRing ring)
    {
        return Arrays.stream(ring.getCoordinates())
                .map(c -> new LatLng(c.getY(), c.getX()))
                .collect(toImmutableList());
    }

    /**
     * Trino function registration that defines converting a lat long to the BIGINT
     * representation of a point's H3 Index.
     *
     * @param lat the latitude of the input point
     * @param lon the longitude of the input point
     * @param res the H3 resolution needed for the conversion
     * @return the binary representation of the H3 cell
     * @throws IllegalArgumentException if the geometry is invalid, resolution is out of range.
     * @throws TrinoException if internal error occurs
     */
    @ScalarFunction("h3_lat_long_to_cell_bigint")
    @Description("Converting POINT(lat lng) GEOMETRY to H3 index at given resolution")
    @SqlType(StandardTypes.BIGINT)
    @SqlNullable
    public static Long latLongToCellBigInt(
            @SqlType(StandardTypes.DOUBLE) double lat,
            @SqlType(StandardTypes.DOUBLE) double lon,
            @SqlType(StandardTypes.INTEGER) long res)
    {
        int resolution = (int) res;
        if (resolution < 0 || resolution > MAX_H3_RESOLUTION) {
            throw new IllegalArgumentException("h3_lat_long_to_cell: resolution must be between 0 and 15");
        }
        if (lon < -180 || lon > 180 || lat > 90 || lat < -90) {
            throw new IllegalArgumentException("h3_latlng_to_cell: Lat-long out of range");
        }
        try {
            return H3Helper.getH3().latLngToCell(lat, lon, toIntExact(res));
        }
        catch (Exception e) {
            log.warn(e, "h3_latlng_to_cell: Unable to process query");
            throw new TrinoException(GENERIC_INTERNAL_ERROR, "h3_latlng_to_cell: Unable to process query", e);
        }
    }

    /**
     * Trino function registration that defines converting a lat long to
     * the hex representation of a point's H3 Index
     *
     * @param lat the latitude
     * @param lon the longitude
     * @param res the H3 resolution needed for the conversion
     * @return the hex string representation of the h3 cell
     * @throws IllegalArgumentException if the geometry is invalid, resolution is out of range.
     * @throws TrinoException if internal error occurs
     */
    @ScalarFunction("h3_lat_long_to_cell_hex")
    @Description("Converting POINT(lat lng) GEOMETRY to H3 index at given resolution")
    @SqlType(StandardTypes.VARCHAR)
    @SqlNullable
    public static Slice geometryToCellHex(
            @SqlType(StandardTypes.DOUBLE) double lat,
            @SqlType(StandardTypes.DOUBLE) double lon,
            @SqlType(StandardTypes.INTEGER) long res)
    {
        int resolution = (int) res;
        if (resolution < 0 || resolution > MAX_H3_RESOLUTION) {
            throw new IllegalArgumentException("h3_lat_long_to_cell: resolution must be between 0 and 15");
        }
        if (lon < -180 || lon > 180 || lat > 90 || lat < -90) {
            throw new IllegalArgumentException("h3_latlng_to_cell: Lat-long out of range");
        }
        try {
            String hex = H3Helper.getH3().latLngToCellAddress(lat, lon, toIntExact(res));
            return Slices.utf8Slice(hex);
        }
        catch (Exception e) {
            log.warn(e, "h3_latlng_to_cell: Unable to process query");
            throw new TrinoException(GENERIC_INTERNAL_ERROR, "h3_latlng_to_cell: Unable to process query", e);
        }
    }

    /**
     * Trino function registration that converts a geometry to its respective
     * BIGINT representation of its H3 index.
     * NOTE: Currently, this function only supports POINT based geometries.
     *
     * @param input the input Geometry
     * @param res the H3 resolution needed for the conversion
     * @return the binary representation of the H3 cell
     * @throws IllegalArgumentException if the geometry is invalid, resolution is out of range.
     * @throws TrinoException if internal error occurs
     */
    @ScalarFunction("h3_point_geometry_to_cell_bigint")
    @Description("Converting POINT(lat lng) GEOMETRY to H3 index at given resolution")
    @SqlType(StandardTypes.BIGINT)
    @SqlNullable
    public static Long pointGeometrytoCellBigInt(
            @SqlType(StandardTypes.GEOMETRY) Slice input,
            @SqlType(StandardTypes.INTEGER) long res)
    {
        int resolution = (int) res;
        if (resolution < 0 || resolution > MAX_H3_RESOLUTION) {
            throw new IllegalArgumentException("h3_lat_long_to_cell: resolution must be between 0 and 15");
        }

        Geometry pointGeomUntyped = deserialize(input);
        if (!TYPENAME_POINT.equals(pointGeomUntyped.getGeometryType())) {
            throw new IllegalArgumentException("h3_latlng_to_cell: input geometry must be a POINT");
        }
        Point pointGeom = (Point) pointGeomUntyped;
        if (pointGeom.getX() < -180 || pointGeom.getX() > 180 || pointGeom.getY() > 90 || pointGeom.getY() < -90) {
            throw new IllegalArgumentException("h3_latlng_to_cell: Lat-long out of range");
        }
        try {
            return H3Helper.getH3().latLngToCell(pointGeom.getY(), pointGeom.getX(), toIntExact(res));
        }
        catch (Exception e) {
            log.warn(e, "h3_latlng_to_cell: Unable to process query");
            throw new TrinoException(GENERIC_INTERNAL_ERROR, "h3_latlng_to_cell: Unable to process query", e);
        }
    }

    /**
     * Trino function registration that converts a geometry to its respective
     * hex representation of its H3 index.
     * NOTE: Currently, this function only supports POINT based geometries.
     *
     * @param input the input Geometry
     * @param res the H3 resolution needed for the conversion
     * @return the hex string representation of the h3 cell
     * @throws IllegalArgumentException if the geometry is invalid, resolution is out of range.
     * @throws TrinoException if internal error occurs
     */
    @ScalarFunction("h3_point_geometry_to_cell_hex")
    @Description("Converting POINT(lat lng) GEOMETRY to H3 index at given resolution")
    @SqlType(StandardTypes.VARCHAR)
    @SqlNullable
    public static Slice pointGeometryToCellHex(
            @SqlType(StandardTypes.GEOMETRY) Slice input,
            @SqlType(StandardTypes.INTEGER) long res)
    {
        int resolution = (int) res;
        if (resolution < 0 || resolution > MAX_H3_RESOLUTION) {
            throw new IllegalArgumentException("h3_lat_long_to_cell: resolution must be between 0 and 15");
        }

        Geometry pointGeomUntyped = deserialize(input);
        if (!TYPENAME_POINT.equals(pointGeomUntyped.getGeometryType())) {
            throw new IllegalArgumentException("h3_latlng_to_cell: input geometry must be a POINT");
        }
        Point pointGeom = (Point) pointGeomUntyped;
        if (pointGeom.getX() < -180 || pointGeom.getX() > 180 || pointGeom.getY() > 90 || pointGeom.getY() < -90) {
            throw new IllegalArgumentException("h3_latlng_to_cell: Lat-long out of range");
        }
        try {
            String hex = H3Helper.getH3().latLngToCellAddress(pointGeom.getY(), pointGeom.getX(), toIntExact(res));
            return Slices.utf8Slice(hex);
        }
        catch (Exception e) {
            log.warn(e, "h3_latlng_to_cell: Unable to process query");
            throw new TrinoException(GENERIC_INTERNAL_ERROR, "h3_latlng_to_cell: Unable to process query", e);
        }
    }

    /**
     * Trino function registration that defines polygontoCell
     *
     * @param input the input Geometry
     * @param res the H3 resolution needed for the conversion
     * @return the binary representation of the H3 cells the polygon is within
     * @throws IllegalArgumentException if the geometry is invalid, resolution is out of range.
     * @throws TrinoException if internal error occurs
     */
    @ScalarFunction("h3_polygon_to_cells")
    @Description("Converting POLYGON() GEOMETRY to H3 index at given resolution")
    @SqlType("array(bigint)")
    @SqlNullable
    public static Block polygonToCells(
            @SqlType(StandardTypes.GEOMETRY) Slice input,
            @SqlType(StandardTypes.INTEGER) long res)
    {
        int resolution = (int) res;
        if (resolution < 0 || resolution > MAX_H3_RESOLUTION) {
            throw new IllegalArgumentException("h3_polygon_to_cell: resolution must be between 0 and 15");
        }
        Geometry polygonGeomUntyped = deserialize(input);
        if (!TYPENAME_POLYGON.equals(polygonGeomUntyped.getGeometryType())) {
            throw new IllegalArgumentException("h3_polygon_to_cell: Invalid polygon geometry");
        }
        try {
            Polygon polygonGeom = (Polygon) polygonGeomUntyped;
            List<LatLng> polygon = linearRingTolatLngList(polygonGeom.getExteriorRing());
            List<List<LatLng>> holes =
                    IntStream.range(0, polygonGeom.getNumInteriorRing())
                            .mapToObj(polygonGeom::getInteriorRingN)
                            .map(H3Function::linearRingTolatLngList)
                            .collect(toImmutableList());
            List<Long> cells = H3Helper.getH3().polygonToCells(polygon, holes, toIntExact(res));
            return H3Helper.longListToBlock(cells);
        }
        catch (Exception e) {
            log.warn(e, "h3_polygon_to_cells: Unable to process query");
            throw new TrinoException(GENERIC_INTERNAL_ERROR, "h3_polygon_to_cells: Unable to process query", e);
        }
    }

    /**
     * Trino function registration that defines h3_cell_to_children
     *
     * @param cell the h3 cell we are looking at
     * @param res the H3 resolution needed for the conversion
     * @return the block representing the children h3 cells
     * @throws IllegalArgumentException if the geometry is invalid, resolution is out of range.
     * @throws TrinoException if internal error occurs
     */
    @ScalarFunction(value = "h3_cell_to_children")
    @Description("Find children of an H3 index at given resolution")
    @SqlType("array(bigint)")
    @SqlNullable
    public static Block cellToChildren(
            @SqlType(StandardTypes.BIGINT) long cell, @SqlType(StandardTypes.INTEGER) long res)
    {
        int resolution = (int) res;
        if (resolution < 0 || resolution > MAX_H3_RESOLUTION) {
            throw new IllegalArgumentException("h3_cells_to_children: resolution must be between 0 and 15");
        }
        try {
            List<Long> children = H3Helper.getH3().cellToChildren(cell, toIntExact(res));
            return H3Helper.longListToBlock(children);
        }
        catch (Exception e) {
            log.warn(e, "h3_cells_to_children: Unable to process query");
            throw new TrinoException(GENERIC_INTERNAL_ERROR, "h3_cells_to_children: Unable to process query", e);
        }
    }

    /**
     * Trino function registration that defines h3_get_resolution
     *
     * @param h3 the index of the h3 cell
     * @return the resolution of the cell
     * @throws TrinoException if internal error occurs
     */
    @ScalarFunction(value = "h3_get_resolution")
    @Description("Convert H3 index to resolution (0-15)")
    @SqlNullable
    @SqlType(StandardTypes.INTEGER)
    public static Long getResolution(@SqlType(StandardTypes.BIGINT) long h3)
    {
        try {
            return Long.valueOf(H3Helper.getH3().getResolution(h3));
        }
        catch (Exception e) {
            log.warn(e, "h3_get_resolution: Unable to process query");
            throw new TrinoException(GENERIC_INTERNAL_ERROR, "h3_get_resolution: Unable to process query", e);
        }
    }

    /**
     * Trino function registration that defines h3_get_res0_cells
     *
     * @return gets all the res0 cells
     */
    @ScalarFunction(value = "h3_get_res0_cells")
    @Description("Get all resolution 0 cells")
    @SqlType("array(bigint)")
    public static Block getRes0Cells()
    {
        Collection<Long> cells = H3Helper.getH3().getRes0Cells();
        return H3Helper.longListToBlock(ImmutableList.copyOf(cells));
    }

    /**
     * Trino function registration that checks to see if an h3 cell is a valid cell
     *
     * @param h3 the index of the h3 cell
     * @return boolean if the cell is valid or not
     * @throws TrinoException if internal error occurs
     */
    @ScalarFunction(value = "h3_is_valid_cell")
    @Description("Check to see if an h3 cell is a valid cell")
    @SqlType(StandardTypes.BOOLEAN)
    public static boolean isValidCell(@SqlType(StandardTypes.BIGINT) long h3)
    {
        try {
            return H3Helper.getH3().isValidCell(h3);
        }
        catch (Exception e) {
            log.warn(e, "h3_is_valid_cell: Unable to process query");
            throw new TrinoException(GENERIC_INTERNAL_ERROR, "h3_is_valid_cell: Unable to process query", e);
        }
    }
}

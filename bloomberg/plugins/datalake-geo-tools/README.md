# Trino Geospatial Tools (trino-geotools)

By: ENG Geo Compute (DRQS G 1431)

## Description

This plugin handles the implementation of all geo-tools-function

### BufferFunction
This plugin implements a new Trino function, `ST_Buffer4326to3857(geometry, distance_in_meters)` that extends the current `ST_Buffer` geospatial function Trino supports. This plugin takes in a geometry that is [EPSG:4326](http://epsg.io/4326), transform the geometry type to [EPSG:3857](http://epsg.io/3857), buffers the transformed geometry by the distance in meters provided by the user, and then transform the buffered geometry back into `EPSG:4326` Coordinate Reference System.

### H3Function
This plugin implements a variety of h3 spatial functions into trino.
`h3_lat_long_to_cell_bigint(lat, lon, resolution)`: This takes a latitude, longitude and an H3 resolution and returns an h3 index (in BigInt form)
`h3_lat_long_to_cell_hex(lat, lon, resolution)`: This takes a latitude, longitude and an H3 resolution and returns an h3 index (in hex form)
`h3_point_geometry_to_cell_bigint(geometry, resolution)`: This takes a geometry (currently only points are supported) and an H3 resolution and returns an h3 index (in BigInt form)
`h3_point_geometry_to_cell_hex(geometry, resolution)`: This takes a geometry (currently only points are supported) and an H3 resolution and returns an h3 index (in hex form)
`h3_polygon_to_cells(geometry, resolution)`: This takes a polygon geometry and an H3 resolution and returns a list of h3 indices the polygon falls under
`h3_cell_to_children(cell, resolution)`: This takes an h3 cell and a different resolution and returns the children of the original cell in that resolution
`h3_getResolution(h3)`: This takes an h3 index and gets the resolution of the index
`h3_is_valid_cell(h3)`: This takes an h3 index and returns if the index is a valid h3 cell.

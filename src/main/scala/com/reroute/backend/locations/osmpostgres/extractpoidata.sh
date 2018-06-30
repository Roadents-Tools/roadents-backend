#!/bin/bash
osmosis --read-pbf $1 \
        --tf accept-nodes name=* \
        --tf accept-ways name=* \
	--tf accept-relations name=* \
        --write-xml "$2.xml"
ogr2ogr -f GeoJSON "$2.json" "$2.xml" points

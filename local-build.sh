#!/usr/bin/env bash
set -e
./mvnw clean package --file standalone.pom.xml
java -cp target/*-with-deps.jar com.onthegomap.planetiler.examples.SingleHighwayLayer --download --area=massachusetts
pmtiles convert data/single-highway-layer.mbtiles data/single-highway-layer.pmtiles
npx serve .

#!/bin/bash
echo osmium tags-filter "$1" way=primary,secondary,tertiary,unclassified,residential,primary_link,secondary_link,tertiary_link,living_street,track,bridleway,cycleway \
    -o "${1%%.osm}-cyclable.osm"

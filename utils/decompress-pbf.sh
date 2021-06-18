#!/bin/bash
osmium cat $1 -o ${1%%.pbf}

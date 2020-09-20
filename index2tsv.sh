#!/bin/bash

if [ $# -lt 2 ]
then
	echo "usage: $0 <index path> <tsv file>"
    exit
fi

RES=$1
INDEX_DIR=$2

mvn exec:java@index2tsv -Dexec.args="$RES $INDEX_DIR"


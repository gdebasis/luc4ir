#!/bin/bash

RES=sample.res
INDEX_DIR=index_trecd45

mvn exec:java@view -Dexec.args="$RES $INDEX_DIR"


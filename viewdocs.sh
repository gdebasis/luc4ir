#!/bin/bash

if [ $# -lt 4 ]
then
	echo "usage: $0 <2 col file comprised of qid docid> <index path> <idfield> <content_field>"
    RES=sample.res
    INDEX_DIR=index_trecd45
    IDFIELD="id"
    CF="words"    
else
    RES=$1
    INDEX_DIR=$2
    IDFIELD=$3
    CF=$4
fi

mvn exec:java@view -Dexec.args="$RES $INDEX_DIR $IDFIELD $CF"



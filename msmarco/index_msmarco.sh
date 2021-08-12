#!/bin/bash

MSMARCO=/Users/debasis/research/common/msmarco
COLL=$MSMARCO/coll/
INDEX=$MSMARCO/index/

cat > index.msmarco.properties << EOF1

coll=$COLL
index=$INDEX
stopfile=stop.txt
parser=line_simple

EOF1

cd ..
mvn exec:java@index -Dexec.args="index.msmarco.properties"
cd -



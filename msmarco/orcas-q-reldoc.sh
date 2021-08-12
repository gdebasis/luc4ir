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
ORCAS_DATA=/Users/debasis/research/common/orcas/uniqdocs.txt
mvn exec:java@orcasreldocs -Dexec.args="$ORCAS_DATA $INDEX"
cd -



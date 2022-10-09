#!/bin/bash

COLLBASE=/Users/debasis/research/common/wiki/
COLL=$COLLBASE/coll/
INDEX=$COLLBASE/index

cat > index.wiki.properties << EOF1

coll=$COLL
index=$INDEX
stopfile=stop.txt
parser=line_simple

EOF1

cd ..
mvn exec:java@index -Dexec.args="wiki/index.wiki.properties"
cd -



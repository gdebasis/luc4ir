#!/bin/bash

if [ $# -lt 2 ]
then
	echo "usage: $0 <collection path> <path to write out index>"
	exit
fi

COLL=$1
INDEX=$2

cat > index.properties << EOF1

coll=$COLL
index=$INDEX
stopfile=stop.txt

EOF1

mvn exec:java@index -Dexec.args="index.properties"



#!/bin/bash

if [ $# -lt 4 ]
then
	echo "usage: $0 <index path> <query file> <qrels file> <feedback (true/false)>"
	exit
fi

INDEX=$1
QUERY=$2
QRELS=$3
FDBK=$4

cat > retrieve.properties << EOF1

index=$1
stopfile=stop.txt

#retrieval
query.file=$QUERY

# small test one
res.file=res.txt

qrels.file=$QRELS

retrieve.runname=lm
lm.lambda=0.4

#workflow switches
feedback=$FDBK
eval=true

fdbk.numtopdocs=10
fdbk.lambda=1

#types allowed: rlm_iid/rlm_cond
rlm.type=rlm_cond

rlm.qe=false
rlm.qe.nterms=10
rlm.qe.newterms.wt=0.2


EOF1

mvn exec:java@retrieve -Dexec.args="retrieve.properties"



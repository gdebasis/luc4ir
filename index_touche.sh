#!/bin/bash

COLLDIR=webis-touche2020/coll
INDEX_DIR=webis-touche2020/index/

if [ ! -d "$INDEX_DIR" ]; then
mkdir $INDEX_DIR 
fi

if [ ! -d "$COLLDIR" ]; then
mkdir $COLLDIR
cd $COLLDIR
wget https://public.ukp.informatik.tu-darmstadt.de/thakur/BEIR/datasets/webis-touche2020.zip
unzip webis-touche2020.zip
mv webis-touche2020/corpus.jsonl . 
rm -rf webis-touche2020*
cd -
fi

cat > touche.properties << EOF1

coll=$COLLDIR
index=$INDEX_DIR

stopfile=stop.txt

parser=json

res.file=touche.res

query.file=webis-touche2020/queries.jsonl
qrels.file=webis-touche2020/qrels.txt

query.fields=t
retrieve.num_wanted=1000

EOF1

mvn exec:java@index -Dexec.args="touche.properties"


#!/bin/bash

cat > retrieve.properties << EOF1
coll=webis-touche2020/coll
index=$INDEX_DIR

stopfile=stop.txt

parser=json

res.file=touche.res

query.file=webis-touche2020/queries.jsonl
qrels.file=webis-touche2020/qrels.txt

query.fields=t
retrieve.num_wanted=1000

EOF1

mvn exec:java -Dexec.mainClass="org.luc4ir.retriever.ToucheRetriever"
trec_eval -l2 -m all_trec webis-touche2020/qrels.txt touche.res

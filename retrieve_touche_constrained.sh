#!/bin/bash

cat > touche.properties << EOF1
coll=webis-touche2020/coll
index=webis-touche2020/index

stopfile=stop.txt

parser=json

res.file=touche.res

query.file=webis-touche2020/queries.jsonl
qrels.file=webis-touche2020/qrels.relonly.txt

query.fields=t
retrieval.constrained=true
retrieve.num_wanted=1000

EOF1


#Generate the constrained qrels
mvn exec:java -Dexec.mainClass="org.luc4ir.retriever.ToucheQrelsBuilder"


#Retrieve constrained
mvn exec:java -Dexec.mainClass="org.luc4ir.retriever.ToucheRetriever"
trec_eval -l2 -m all_trec webis-touche2020/qrels.relonly.txt.constrained touche.res.constrained

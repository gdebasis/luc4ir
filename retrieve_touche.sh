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
retrieval.constrained=false
retrieve.num_wanted=10

EOF1

proconratio=`mvn exec:java -Dexec.mainClass="org.luc4ir.retriever.ToucheRetriever" | grep "Pro-Con ratio =" | awk '{print $NF}'`
ndcg=`trec_eval -l2 -m all_trec webis-touche2020/qrels.relonly.txt touche.res | grep -w "ndcg_cut_10" | awk '{print $NF}'`
fscore=`echo "$proconratio $ndcg" | awk '{print 2*$1*$2/($1+$2)}'`

echo "NDCG@10 = $ndcg"
echo "Fairness@10 = $proconratio"
echo "F-score@10 = $fscore"

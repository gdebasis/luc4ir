#!/bin/bash
RESFILE=../Fire_Submission_Results/task1_adapt/F5_0_Model1.tsv
RELFILE=task1.rel

cat $RESFILE | awk '{for (i=1;i<=NF; i++) printf("%s ",tolower($i)); printf("\n");}' > tmp
paste tmp $RELFILE | awk -F '\t' '{print $1"\t"$3}' > pred_rel.txt
perl -p -i -e 's/ /\t/' pred_rel.txt

rm tmp

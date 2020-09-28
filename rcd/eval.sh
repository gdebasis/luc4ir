#!/bin/bash

if [ $# -lt 2 ]
then
	echo "usage: $0 <folder containing task1 runs> <rel file (correct one for train or test)>"
    exit
fi

#grep "<title>" ~/research/fire2020/rcd/eval/topics_trec_formatted.txt | sed 's/<title>//'|sed 's/<\/title>//'| awk '{print NR"\t"tolower($0)}' | sed 's/\.//g' | sed 's/\!//g'

RESDIR=$1
RELFILE=$2
EQUIV_QRIES=rcd/equiv.txt

for RESFILE in `find $RESDIR -type f`
do
echo "Evaluating $RESFILE"

RESFILENAME=$(basename $RESFILE)
PREDRELMERGED=pred_rel_$RESFILENAME

cat $RESFILE | awk '{for (i=1;i<=NF; i++) printf("%s ",tolower($i)); printf("\n");}' > tmp
paste tmp $RELFILE | awk -F '\t' '{print $1"\t"$3}' > $PREDRELMERGED
perl -p -i -e 's/ /\t/' $PREDRELMERGED

rm tmp

cd ..
mvn exec:java@rcd_task1_eval -Dexec.args="rcd/$PREDRELMERGED $EQUIV_QRIES"
cd -

rm $PREDRELMERGED
done

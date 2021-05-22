#Avg query length
cat data/topics/all.txt | awk '{s += NF} END{print s/NR}'

#Avg #rel
for f in `find data/qrels -type f`
do
	cat $f >> tmp
done 

cat tmp| awk '{if ($4>0) s++} END{print s/249}'
rm tmp		

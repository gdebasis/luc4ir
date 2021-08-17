cat qid_topdoc.txt | awk -F '\t' '{print $2}' | awk '{for (i=1;i<=NF;i++) print $i}'|sort|uniq > vocab.txt
awk 'FNR==NR{v[$1]=$1; next} {if ($1 in v) print $0}' vocab.txt ~/research/common/wordvecs/glove.6B.300d.txt > glove.subset


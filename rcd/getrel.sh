grep "<title>" topics_trec_formatted.txt | sed 's/<title>//'|sed 's/<\/title>//'| awk '{print NR"\t"$0}' > allrels_task1.tsv

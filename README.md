# Mavenized Lucene IR Framework for TREC Ad-hoc Document Retrieval

This code is distributed in the hope that it'll be useful for IR practitioners and students who want to get started with retrieving documents from a collection and measure effectiveness with standard evaluation metrics.


### To index TREC document disks 4/5

Due to the lack of space on github, please download the TREC disks 4/5 collection from [here](https://gla-my.sharepoint.com/:f:/g/personal/debasis_ganguly_glasgow_ac_uk/EgEuteOhyX5Lj-EpBdKjzRoB_lo91gag-BSEj5pqPpwumw?e=epVZg3)

After downloading the collection and unzipping it, build the index by executing the following script
```
./index_trecd45 <path to the collection>
```

For retrieval, simply run the script
```
./retrieve_trecd45.sh <INDEX-PATH> <QUERY FILE> <QRELS FILE>
```
which executes a series of queries from a TREC formatted topic file (using the LM-Dir retrieval model) and reports [MAP]([https://en.wikipedia.org/wiki/Evaluation_measures_(information_retrieval)#Mean_average_precision](https://en.wikipedia.org/wiki/Evaluation_measures_(information_retrieval)#Mean_average_precision)).


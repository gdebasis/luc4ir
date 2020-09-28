## RCD Task-1 Evaluation

The [RCD](https://rcd2020firetask.github.io/RCD2020FIRETASK/) (Retrieval from Conversational Dialogues) track at [FIRE-2020](http://fire.irsi.res.in/fire/2020/home) (Forum of Information Retrieval and Evaluation) focuses on retrieving relevant passages from Wikipedia to help understand the meaning of a conversation. 

Given a piece of dialogue between two (or more) communicating entities, a first step is to identify the pieces of text that require contextualization. Although this actually depends on a user's knowledge and expertise, for the sake of simplicity this task assumes that such subjective elements are not considered. Given a dataset of relevant text spans (that were judged to be likely associated with an information need, or in other words these are worth contextualizing), and a set of predicted text spans (typically with the help of an automated extractor), the task uses the character n-gram based [BLEU](https://en.wikipedia.org/wiki/BLEU) metric to evaluate the effectiveness of the prediction.

Both the predicted and the ground-truth data contain two columns of records in a tab separated format, where the first column denotes the query id and the second one denotes the text span (predicted or ground-truth). To evaluate, simply execute

```
./eval.sh <predicted file> <ground-truth file>
```

We provide two ground-truth files named `task1_train.rel` and `task1_test.rel`, and also a sample predicted file `nqc.tsv` (which slides a window of length 5 and predicts as output the span yielding the highest [NQC](https://dl.acm.org/doi/10.1145/2180868.2180873) score).

Before executing the script, you have to execute
``
mvn compile
``
from the project folder (i.e. where `pom.xml` resides).

If everything is in order, after executing `./eval.sh nqc.tsv task1_test.rel`, you should see the following output.
```
BLEU = 0.16270196
```


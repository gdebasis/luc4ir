package org.luc4ir.evaluator;

import java.util.*;
import java.util.stream.Collectors;

public class RetrievedResults implements Comparable<RetrievedResults> {
    String qid;
    List<ResultTuple> rtuples;
    int numRelRet;
    float avgP;
    PerQueryRelDocs relInfo;

    // try rel/ret
    static final String computeNDCGOver = "ret"; // change this to ret when u want the idea ranked list from ur ret list

    public RetrievedResults(String qid) {
        this.qid = qid;
        this.rtuples = new ArrayList<>(1000);
        avgP = -1;
        numRelRet = -1;
    }

    void addTuple(String docName, int rank) {
        rtuples.add(new ResultTuple(docName, rank));
    }

    public String toString() {
        StringBuffer buff = new StringBuffer();
        for (ResultTuple rt : rtuples) {
            buff.append(qid).append("\t").
                    append(rt.docName).append("\t").
                    append(rt.rank).append("\t").
                    append(rt.rel).append("\n");
        }
        return buff.toString();
    }

    void fillRelInfo(PerQueryRelDocs relInfo) {
        if (relInfo == null)
            return;
        String qid = relInfo.qid;

        for (ResultTuple rt : rtuples) {
            Float relIntObj = relInfo.relMap.get(rt.docName);
            rt.rel = relIntObj == null? 0 : relIntObj.intValue();
        }
        this.relInfo = relInfo;
    }

    float computeRR() {
        float rr = 0;
        for (ResultTuple tuple : this.rtuples) {
            if (tuple.rel < Evaluator.threshold)
                continue;
            rr = 1/(float)tuple.rank;
        }
        return rr;
    }

    float computeAP() {
        if (avgP > -1)
            return avgP;

        float prec = 0;
        int numRel = relInfo.numRel;
        int numRelSeen = 0;
        for (ResultTuple tuple : this.rtuples) {
            if (tuple.rel < Evaluator.threshold)
                continue;
            numRelSeen++;
            prec += numRelSeen/(float)(tuple.rank);
        }
        numRelRet = numRelSeen;
        prec = numRel==0? 0 : prec/(float)numRel;
        this.avgP = prec;

        return prec;
    }

    float computeDCG(List<ResultTuple> rtuples, int cutoff) {
        float dcgSum = 0;
        int rank = 1;
        for (ResultTuple tuple : rtuples) {
            float dcg = tuple.rel/(float)(Math.log(rank+1)/Math.log(2));
            dcgSum += dcg;
            if (rank >= cutoff)
                break;
            rank++;
        }
        return dcgSum;
    }

    List<ResultTuple> constructIdealList(Map<String, Float> docRelMap) { // Sort in decreasing order all the relevant docs
        List<ResultTuple> idealRes = new ArrayList<>();
        for (String docName: docRelMap.keySet()) {
            idealRes.add(new ResultTuple(docName, 0, docRelMap.get(docName))); // rank=0 a placeholder
        }
        Collections.sort(idealRes, new Comparator<ResultTuple>() {
            @Override
            public int compare(ResultTuple thisObj, ResultTuple thatObj) { // descending in rel values
                return thisObj.rel > thatObj.rel? -1 : thisObj.rel == thatObj.rel? 0 : 1;
            }
        });

        // assign the ranks
        int rank = 1;
        for (ResultTuple rt: idealRes) {
            rt.rank = rank++;
        }
        return idealRes;
    }

    double log2(float x) {
        return Math.log(x)/Math.log(2);
    }

    float calcDCG(List<Float> relLabels) {
        int rank = 1;
        float ndcg = 0;
        for (Float relLabel: relLabels) {
            ndcg += (float)relLabel.intValue()/log2(rank+1);
            rank++;
        }
        return ndcg;
    }

    public float computeNdcg(int cutoff) {
        List<Float> rels =
                relInfo.relMap.values()
                        .stream()
                        .sorted(Comparator.reverseOrder())  // more relevant at a smaller rank value is ideal
                        .limit(cutoff)
                        .collect(Collectors.toList());

        float idcg = calcDCG(rels);
        if (idcg == 0)
            return 0;

        List<Float> rets = this.rtuples.stream()
                .limit(cutoff)
                .map(x->x.rel)
                .collect(Collectors.toList());
        float dcg = calcDCG(rets);

        //System.out.println(rels);
        //System.out.println(rets);

        //System.out.println(String.format("%.4f %.4f", dcg, idcg));
        return dcg/idcg;
    }

    float precAtTop(int k) {
        int numRelSeen = 0;
        int numSeen = 0;
        for (ResultTuple tuple : this.rtuples) {
            if (tuple.rel >= 1)
                numRelSeen++;
            if (++numSeen >= k)
                break;
        }
        return numRelSeen/(float)k;
    }

    float computeRecall() {
        if (numRelRet > -1)
            return numRelRet;
        int numRelSeen = 0;
        for (ResultTuple tuple : this.rtuples) {
            if (tuple.rel < 1)
                continue;
            numRelSeen++;
        }
        numRelRet = numRelSeen;
        return numRelSeen;
    }

    @Override
    public int compareTo(RetrievedResults that) {
        return this.qid.compareTo(that.qid);
    }
}


package org.luc4ir.evaluator;

import java.util.*;

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
        String qid = relInfo.qid;

        for (ResultTuple rt : rtuples) {
            Float relIntObj = relInfo.relMap.get(rt.docName);
            rt.rel = relIntObj == null? 0 : relIntObj.intValue();
        }
        this.relInfo = relInfo;
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

    float computeNDCG(int ntops) {
        float dcg = 0, idcg = 0;
        List<ResultTuple> idealTuples;
        Map<String, Float> docRelMap = new HashMap<>();

        if (computeNDCGOver.equals("rel"))
            docRelMap = this.relInfo.relMap;
        else {
            for (ResultTuple rt: this.rtuples) {
                if (rt.rel > 0)
                    docRelMap.put(rt.docName, rt.rel);
            }
        }

        idealTuples = constructIdealList(docRelMap);
        dcg = computeDCG(this.rtuples, ntops);
        idcg = computeDCG(idealTuples, ntops);

        return idcg>0? dcg/idcg: 0;
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


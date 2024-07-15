package org.luc4ir.fusion;

import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHits;
import org.luc4ir.evaluator.ResultTuple;
import org.luc4ir.genutils.ScoreDocComparator;
import org.luc4ir.genutils.ScoreDocUtils;

import java.util.*;

public class RRFusion implements Fusion {

    HashMap<Integer, ResultTuple> topDocsToMap(ScoreDoc[] scoreDocs) {
        HashMap<Integer, ResultTuple> map = new HashMap<>();
        int rank = 0;
        for (ScoreDoc sd: scoreDocs) {
            map.put(sd.doc, new ResultTuple(String.valueOf(sd.doc), ++rank));
        }
        return map;
    }

    float log_base_2(double x) {
        return (float)(Math.log(x)/Math.log(2));
    }

    float rr_score(int rank) {
        //return 1.0f/log_base_2(1+rank);
        return 1.0f/rank;
    }

    void updateScores(int docId, HashMap<Integer, ResultTuple> scoreMap, HashMap<Integer, ScoreDoc> fusedList, int M) {
        ResultTuple rt = scoreMap.get(docId);
        int rank = rt==null ? M : rt.rank();
        ScoreDoc sd = fusedList.get(docId);
        if (sd == null) {
            sd = new ScoreDoc(docId, 0);
            fusedList.put(docId, sd);
        }
        sd.score += rr_score(rank);
    }

    @Override
    public TopDocs combine(ScoreDoc[] a, ScoreDoc[] b, int k) {
        HashMap<Integer, ResultTuple> mapA = topDocsToMap(a);
        HashMap<Integer, ResultTuple> mapB = topDocsToMap(b);
        int nA = mapA.size();
        int nB = mapB.size();
        final int M = nA+nB;

        HashMap<Integer, ScoreDoc> fusedList = new HashMap<>();
        Set<Integer> allDocIds = new HashSet<>(mapA.keySet());
        allDocIds.addAll(mapB.keySet());

        for (int docId: allDocIds) {
            updateScores(docId, mapA, fusedList, M);
            updateScores(docId, mapB, fusedList, M);
        }

        int nwanted = Math.min(k, fusedList.size());
        ScoreDoc[] combinedScoreDocs = fusedList.values()
                .stream().sorted(new ScoreDocComparator())
                .limit(nwanted)
                .toArray(ScoreDoc[]::new);

        return new TopDocs(new TotalHits(nwanted, TotalHits.Relation.EQUAL_TO), combinedScoreDocs);
    }

    public static void main(String[] args) {
        ScoreDoc[] sA = {
                new ScoreDoc(1, 2.0f),
                new ScoreDoc(2, 1.8f),
                new ScoreDoc(3, 1.2f),
                new ScoreDoc(4, 1.0f),
                new ScoreDoc(5, 0.6f)
        };

        ScoreDoc[] sB = {
                new ScoreDoc(7, 3.0f),
                new ScoreDoc(3, 2.8f),
                new ScoreDoc(5, 1.5f),
                new ScoreDoc(9, 0.9f),
                new ScoreDoc(5, 0.2f)
        };

        TopDocs fused = new RRFusion().combine(sA, sB, 10);
        System.out.println(ScoreDocUtils.toString(fused.scoreDocs));
    }
}

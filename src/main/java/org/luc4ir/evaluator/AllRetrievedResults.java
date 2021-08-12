package org.luc4ir.evaluator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Map;
import java.util.TreeMap;

public class AllRetrievedResults {
    Map<String, RetrievedResults> allRetMap;
    String resFile;
    AllRelRcds allRelInfo;

    public AllRetrievedResults(String resFile) {
        this.resFile = resFile;
        allRetMap = new TreeMap<>();
    }

    public void load() {
        String line;
        try (FileReader fr = new FileReader(resFile);
             BufferedReader br = new BufferedReader(fr); ) {
            while ((line = br.readLine()) != null) {
                storeRetRcd(line);
            }
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }

    void storeRetRcd(String line) {
        String[] tokens = line.split("\\s+");
        String qid = tokens[0];
        RetrievedResults res = allRetMap.get(qid);
        if (res == null) {
            res = new RetrievedResults(qid);
            allRetMap.put(qid, res);
        }
        res.addTuple(tokens[2], Integer.parseInt(tokens[3]));
    }

    public String toString() {
        StringBuffer buff = new StringBuffer();
        for (Map.Entry<String, RetrievedResults> e : allRetMap.entrySet()) {
            RetrievedResults res = e.getValue();
            buff.append(res.toString()).append("\n");
        }
        return buff.toString();
    }

    public void fillRelInfo(AllRelRcds relInfo) {
        for (Map.Entry<String, RetrievedResults> e : allRetMap.entrySet()) {
            RetrievedResults res = e.getValue();
            PerQueryRelDocs thisRelInfo = relInfo.getRelInfo(String.valueOf(res.qid));
            res.fillRelInfo(thisRelInfo);
        }
        this.allRelInfo = relInfo;
    }

    String computeAll() {
        StringBuffer buff = new StringBuffer();
        float map = 0f;
        float gm_ap = 0f;
        float avgRecall = 0f;
        float numQueries = (float)allRetMap.size();
        float pAt5 = 0f;
        float ndcg = 0;
        float ndcg_5 = 0;

        for (Map.Entry<String, RetrievedResults> e : allRetMap.entrySet()) {
            RetrievedResults res = e.getValue();
            float ap = res.computeAP();
            map += ap;
            //gm_ap += ap>0? Math.log(ap): 0;
            avgRecall += res.computeRecall();
            pAt5 += res.precAtTop(5);
            if (Evaluator.graded) {
                float thisNDCG = res.computeNDCG(res.rtuples.size());
                float thisNDCG_5 = res.computeNDCG(5);
                ndcg += thisNDCG;
                ndcg_5 += thisNDCG_5;
            }
        }

        buff.append("recall:\t").append(avgRecall/(float)allRelInfo.getTotalNumRel()).append("\n");
        buff.append("map:\t").append(map/numQueries).append("\n");
        //buff.append("gmap:\t").append((float)Math.exp(gm_ap/numQueries)).append("\n");
        buff.append("P@5:\t").append(pAt5/numQueries).append("\n");
        if (Evaluator.graded) {
            buff.append("nDCG:\t").append(ndcg/numQueries).append("\n");
            buff.append("nDCG@5:\t").append(ndcg_5/numQueries).append("\n");
        }

        return buff.toString();
    }
}



/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.evaluator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;


/**
 *
 * @author Debasis
 */

class PerQueryRelDocs {
    String qid;
    HashMap<String, Float> relMap; // keyed by docid, entry stores the rel value (>0)
    int numRel;
    
    public PerQueryRelDocs(String qid) {
        this.qid = qid;
        numRel = 0;
        relMap = new HashMap<>();
    }
    
    void addTuple(String docId, float rel) {
        if (relMap.get(docId) != null)
            return;
        if (rel > 0) {
            numRel++;
            relMap.put(docId, rel);
        }
    }    
}

class AllRelRcds {
    String qrelsFile;
    HashMap<String, PerQueryRelDocs> perQueryRels;
    int totalNumRel;

    public AllRelRcds(String qrelsFile) {
        this.qrelsFile = qrelsFile;
        perQueryRels = new HashMap<>();
        totalNumRel = 0;
    }
    
    int getTotalNumRel() {
        if (totalNumRel > 0)
            return totalNumRel;
        
        for (Map.Entry<String, PerQueryRelDocs> e : perQueryRels.entrySet()) {
            PerQueryRelDocs perQryRelDocs = e.getValue();
            totalNumRel += perQryRelDocs.numRel;
        }
        return totalNumRel;
    }
    
    void load() throws Exception {
        FileReader fr = new FileReader(qrelsFile);
        BufferedReader br = new BufferedReader(fr);
        String line;
        
        while ((line = br.readLine()) != null) {
            storeRelRcd(line);
        }
        br.close();
        fr.close();
    }
    
    void storeRelRcd(String line) {
        String[] tokens = line.split("\\s+");
        String qid = tokens[0];
        PerQueryRelDocs relTuple = perQueryRels.get(qid);
        if (relTuple == null) {
            relTuple = new PerQueryRelDocs(qid);
            perQueryRels.put(qid, relTuple);
        }
        relTuple.addTuple(tokens[2], Integer.parseInt(tokens[3]));
    }
    
    public String toString() {
        StringBuffer buff = new StringBuffer();
        for (Map.Entry<String, PerQueryRelDocs> e : perQueryRels.entrySet()) {
            PerQueryRelDocs perQryRelDocs = e.getValue();
            buff.append(e.getKey()).append("\n");
            for (Map.Entry<String, Float> rel : perQryRelDocs.relMap.entrySet()) {
                String docName = rel.getKey();
                float relVal = rel.getValue();
                buff.append(docName).append(",").append(relVal).append("\t");
            }
            buff.append("\n");
        }
        return buff.toString();
    }
    
    PerQueryRelDocs getRelInfo(String qid) {
        return perQueryRels.get(qid);
    }
}

class ResultTuple implements Comparable<ResultTuple> {
    String docName; // doc name
    int rank;       // rank of retrieved document
    float rel;    // is this relevant? comes from qrel-info

    public ResultTuple(String docName, int rank) {
        this.docName = docName;
        this.rank = rank;
    }

    public ResultTuple(String docName, int rank, float rel) {
        this.docName = docName;
        this.rank = rank;
        this.rel = rel;
    }

    @Override
    public int compareTo(ResultTuple t) {
        return rank < t.rank? -1 : rank == t.rank? 0 : 1;
    }
}

class RetrievedResults implements Comparable<RetrievedResults> {
    String qid;
    List<ResultTuple> rtuples;
    int numRelRet;
    float avgP;    
    PerQueryRelDocs relInfo;
    
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

    List<ResultTuple> constructIdealList() { // Sort in decreasing order all the relevant docs
        List<ResultTuple> idealRes = new ArrayList<>();
        for (String docName: this.relInfo.relMap.keySet()) {
            idealRes.add(new ResultTuple(docName, 0, this.relInfo.relMap.get(docName))); // rank=0 a placeholder
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
        List<ResultTuple> idealTuples = constructIdealList();
                
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

class AllRetrievedResults {
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
            gm_ap += ap>0? Math.log(ap): 0;
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
        buff.append("gmap:\t").append((float)Math.exp(gm_ap/numQueries)).append("\n");
        buff.append("P@5:\t").append(pAt5/numQueries).append("\n");
        if (Evaluator.graded) {
            buff.append("nDCG:\t").append(ndcg/numQueries).append("\n");
            buff.append("nDCG@5:\t").append(ndcg_5/numQueries).append("\n");
        }
        
        return buff.toString();
    }    
}

public class Evaluator {
    AllRelRcds relRcds;
    AllRetrievedResults retRcds;
    static boolean graded;
    static int threshold;
    
    public Evaluator(String qrelsFile, String resFile) {
        relRcds = new AllRelRcds(qrelsFile);
        retRcds = new AllRetrievedResults(resFile);
        graded = true;
        threshold = 1;
    }
    
    public Evaluator(Properties prop) {
        String qrelsFile = prop.getProperty("qrels.file");
        String resFile = prop.getProperty("res.file");
        graded = Boolean.parseBoolean(prop.getProperty("evaluate.graded", "false"));
        if (graded)
            threshold = Integer.parseInt(prop.getProperty("evaluate.graded_to_bin.threshold", "1"));
        else
            threshold = 1;
        relRcds = new AllRelRcds(qrelsFile);
        retRcds = new AllRetrievedResults(resFile);        
    }
    
    public void load() throws Exception {
        relRcds.load();
        retRcds.load();
    }
    
    public void fillRelInfo() {
        retRcds.fillRelInfo(relRcds);
    }
    
    public String computeAll() {
        return retRcds.computeAll();
    }
    
    @Override
    public String toString() {
        StringBuffer buff = new StringBuffer();
        buff.append(relRcds.toString()).append("\n");
        buff.append(retRcds.toString());
        return buff.toString();
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            args = new String[1];
            args[0] = "init.properties";
        }
        try {
            Properties prop = new Properties();
            prop.load(new FileReader(args[0]));
            
            String qrelsFile = prop.getProperty("qrels.file");
            String resFile = prop.getProperty("res.file");
            
            Evaluator evaluator = new Evaluator(qrelsFile, resFile);
            evaluator.load();
            evaluator.fillRelInfo();
            System.out.println(evaluator.computeAll());
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        
    }
    
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.evaluator;
import java.io.*;
import java.util.*;
import org.apache.commons.io.FileUtils;
import org.apache.james.mime4j.Charsets;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.luc4ir.indexing.TrecDocIndexer;

/**
 *
 * @author debforit
 */
class PredRelPair {
    String id;
    String pred;
    String rel;

    public PredRelPair(String id, String pred, String rel) {
        this.id = id;
        this.pred = TrecDocIndexer.analyze(new StandardAnalyzer(), pred);
        this.rel = TrecDocIndexer.analyze(new StandardAnalyzer(), rel);
    }
    
    public float getSim(int ngramSize) {
        DocVector predvec = new DocVector(pred, ngramSize);
        DocVector refvec = new DocVector(rel, ngramSize);
        
        return ngramSize>0? predvec.cosineSim(refvec): predvec.jaccard(refvec);
    }
    
    @Override
    public String toString() {
        return id + "\t" + pred + "\t" + rel;
    }
}


public class RCDEvaluator {
    
    Map<String, List<String>> equivQueries;
    Map<String, PredRelPair> predRelPairs;
    
    //static public int N = 5;

    RCDEvaluator(String predRelTsv, String equivFile) throws Exception {
        loadEquivQueries(equivFile);
        loadPredRelPair(predRelTsv);
    }

    final void loadEquivQueries(String equivFile) throws Exception {
        FileReader fr = new FileReader(equivFile);
        BufferedReader br = new BufferedReader(fr);
        String line;
        equivQueries = new HashMap<>();
        int i=1;

        while ((line = br.readLine()) != null) {
            List<String> queries = new ArrayList<>();

            String[] tokens = line.split("\\s+");
            for (String token: tokens) {
                queries.add(token);
            }

            equivQueries.put(String.format("Q%d", i), queries);
            i++;
        }

        br.close();
        fr.close();
    }

    final void loadPredRelPair(String predRelFile) throws IOException {
        predRelPairs = new HashMap<>();
        
        List<String> lines = FileUtils.readLines(new File(predRelFile), Charsets.UTF_8);
        for (String line: lines) {
            String[] tokens = line.split("\t");
            PredRelPair predrel = new PredRelPair(tokens[0], tokens[1], tokens[2]);
            predRelPairs.put(predrel.id, predrel);
        }
    }
    
    float printAvgNGramMatchStats(int ngramValue) {
        float avg = 0;
        int numEvaluated = 0;
        boolean evaluate;
        
        for (List<String> equivQueries: equivQueries.values()) {
            // Merge the rel/pred sets for this query group...
            StringBuffer relTextBuff = new StringBuffer();
            StringBuffer predTextBuff = new StringBuffer();
            StringBuffer idBuff = new StringBuffer();
            
            evaluate = true;
            
            for (String qid: equivQueries) {
                PredRelPair tuple = predRelPairs.get(qid);
                if (tuple==null) {
                    evaluate = false;
                    break;
                }
                
                predTextBuff.append(tuple.pred).append(" ");
                relTextBuff.append(tuple.rel).append(" ");
                idBuff.append(qid).append(" ");
            }
            if (!evaluate)
                continue;
            
            String predTextStr = predTextBuff.toString().trim();
            String relTextStr = relTextBuff.toString().trim();
            String id = idBuff.toString().trim();
            
            PredRelPair tuple = new PredRelPair(id, predTextStr, relTextStr);
            
            float sim = tuple.getSim(ngramValue);
            System.out.println(tuple.toString() + "\t" + sim);
            avg += sim;
            numEvaluated++;
        }
        
        avg = avg/(float)numEvaluated;
        System.out.println(String.format("Avg. %d-gram Cosine-Sim = %.4f", ngramValue, avg));
        return avg;
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: java Evaluator <pred-rel file> <equiv query list>");
            System.err.println("Evaluating on default arguments");

            // else demo on the sample provide...
            args = new String[3];
            args[0] = "rcd/pred_rel.txt";
            args[1] = "rcd/equiv.txt";  // ths would only be present after u run the index
        }
        
        try {
            RCDEvaluator rcdeval = new RCDEvaluator(args[0], args[1]);
            float n_sum = 12.0f; // 3+4+5=12
            float wavg = 0;
            for (int n=3; n<=5; n++) {
                float w = n/n_sum; // the higher the n the higher the weight
                float val = rcdeval.printAvgNGramMatchStats(n);
                wavg += w*val;
            }
            System.out.println("BLEU = " + wavg);
            
            rcdeval.printAvgNGramMatchStats(0);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}


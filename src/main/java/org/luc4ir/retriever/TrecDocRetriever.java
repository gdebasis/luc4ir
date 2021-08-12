/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.retriever;

/**
 *
 * @author Debasis
 */

import org.luc4ir.evaluator.Evaluator;
import org.luc4ir.evaluator.PerQueryRelDocs;
import org.luc4ir.feedback.RelevanceModelConditional;
import org.luc4ir.feedback.RelevanceModelIId;
import org.luc4ir.feedback.RetrievedDocTermInfo;
import org.luc4ir.indexing.TrecDocIndexer;
import java.io.*;
import java.util.*;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.*;
import org.luc4ir.trec.TRECQuery;
import org.luc4ir.trec.TRECQueryParser;

/**
 *
 * @author Debasis
 */

public class TrecDocRetriever {

    TrecDocIndexer indexer;
    IndexReader reader;
    IndexSearcher searcher;
    int numWanted;
    Properties prop;
    String runName;
    String kdeType;
    boolean postRLMQE;
    boolean postQERerank;
    Similarity model;
    
    public TrecDocRetriever(String propFile, Similarity sim) throws Exception {        
        indexer = new TrecDocIndexer(propFile);
        prop = indexer.getProperties();
        
        try {
            File indexDir = indexer.getIndexDir();
            System.out.println("Running queries against index: " + indexDir.getPath());
            
            reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
            searcher = new IndexSearcher(reader);
            
            this.model = sim;
            searcher.setSimilarity(sim);
            
            numWanted = Integer.parseInt(prop.getProperty("retrieve.num_wanted", "1000"));
            runName = prop.getProperty("retrieve.runname", "lm");
            
            kdeType = prop.getProperty("rlm.type", "uni");
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }        
    }
    
    public Properties getProperties() { return prop; }
    public IndexReader getReader() { return reader; }
    public IndexSearcher getSearcher() { return searcher; }
    
    public List<TRECQuery> constructQueries() throws Exception {        
        String queryFile = prop.getProperty("query.file");
        TRECQueryParser parser = new TRECQueryParser(queryFile, indexer.getAnalyzer());
        parser.parse();
        return parser.getQueries();
    }
    
    // Computes the similarity of two queries based on KL-divergence
    // of the estimated relevance models. More precisely, if Q1 and Q2
    // are two queries, the function computes pi = P(w|Qi,TOP(Qi)) for i=1,2
    // It then computes KL(p1, p2)
    public float computeQuerySimilarity(TRECQuery q1, TRECQuery q2, int ntop) throws Exception {
        
        // Get the top docs for both q1 and q2
        TopDocs q1_topDocs = searcher.search(q1.getLuceneQueryObj(), ntop);
        TopDocs q2_topDocs = searcher.search(q2.getLuceneQueryObj(), ntop);
        
        // Estimate the relevance models for each query and associated top-docs
        RelevanceModelIId rm_q1 = new RelevanceModelConditional(this, q1, q1_topDocs);
        RelevanceModelIId rm_q2 = new RelevanceModelConditional(this, q2, q2_topDocs);
        
        Map<String, RetrievedDocTermInfo> q1_topTermMap = rm_q1.getRetrievedDocsTermStats().getTermStats();
        Map<String, RetrievedDocTermInfo> q2_topTermMap = rm_q2.getRetrievedDocsTermStats().getTermStats();
        
        // Merge the two models
        Map<String, RetrievedDocTermInfo> mergedAvgModel =
                mergeRelevanceModels(q1_topTermMap, q2_topTermMap);
        // JS divergence
        return (klDiv(q1_topTermMap, mergedAvgModel) + klDiv(q2_topTermMap, mergedAvgModel))/2;
    }
    
    Map<String, RetrievedDocTermInfo> mergeRelevanceModels(
            Map<String, RetrievedDocTermInfo> q1_topTermMap,
            Map<String, RetrievedDocTermInfo> q2_topTermMap) {
        
        float wt = 0;
        Map<String, RetrievedDocTermInfo> merged_topTermMap = new HashMap<>();
        
        for (RetrievedDocTermInfo a : q1_topTermMap.values()) {
            RetrievedDocTermInfo b = q2_topTermMap.get(a.getTerm());
            wt = a.getWeight();
            if (b != null)
                wt += b.getWeight();
                
            a.setWeight(wt/2);
            merged_topTermMap.put(a.getTerm(), a);
        }

        for (RetrievedDocTermInfo a : q2_topTermMap.values()) {
            RetrievedDocTermInfo b = q1_topTermMap.get(a.getTerm());
            wt = a.getWeight();
            if (b != null)
                wt += b.getWeight();
                
            a.setWeight(wt/2);
            merged_topTermMap.put(a.getTerm(), a);
        }
        
        return merged_topTermMap;
    }
    
    float klDiv(Map<String, RetrievedDocTermInfo> q1_topTermMap, Map<String, RetrievedDocTermInfo> q2_topTermMap) {
        float kldiv = 0, a_wt, b_wt;
        
        for (RetrievedDocTermInfo a : q1_topTermMap.values()) {
            String term = a.getTerm(); // for each term in model the first model
            
            // Get this term's weight in the second model
            RetrievedDocTermInfo b = q2_topTermMap.get(term);
            if (b == null)
                continue;
            
            a_wt = a.getWeight();
            b_wt = b.getWeight();
            kldiv += a_wt * Math.log(a_wt/b_wt);
        }
        return kldiv;
    }

    TopDocs retrieve(TRECQuery query) throws IOException {
        return searcher.search(query.getLuceneQueryObj(), numWanted);
    }
    
    public void retrieveAll() throws Exception {
        TopDocs topDocs;
        Map<String, TopDocs> topDocsMap = new HashMap<>();
        Evaluator evaluator = null;

        String resultsFile = prop.getProperty("res.file");        
        FileWriter fw = new FileWriter(resultsFile);
        BufferedWriter bw = new BufferedWriter(fw);

        List<TRECQuery> queries = constructQueries();
        int start = Integer.parseInt(prop.getProperty("qid.start", "0"));
        int end = Integer.parseInt(prop.getProperty("qid.end", "-1"));

        for (TRECQuery query : queries) {

            if (Integer.parseInt(query.id) < start) continue;
            if (Integer.parseInt(query.id) > end) break;

            // Print query
            System.out.println("Executing query: " + query.getLuceneQueryObj());
            
            // Retrieve results
            topDocs = retrieve(query);
            topDocsMap.put(query.id, topDocs);

            // Apply feedback
            if (Boolean.parseBoolean(prop.getProperty("feedback")) && topDocs.scoreDocs.length > 0) {
                topDocs = applyFeedback(query, topDocs);
            }
            
            // Save results
            saveRetrievedTuples(bw, query, topDocs);
        }

        bw.close();
        fw.close();

        if (Boolean.parseBoolean(prop.getProperty("eval"))) {
            evaluator = evaluate();
        }

        resultsFile = prop.getProperty("res.file");
        fw = new FileWriter(resultsFile + ".rel");
        bw = new BufferedWriter(fw);

        for (TRECQuery query : queries) {
            if (Integer.parseInt(query.id) < start) continue;
            if (Integer.parseInt(query.id) > end) break;

            saveRetrievedTuples(bw, query, topDocsMap.get(query.id), evaluator);
        }

        bw.close();
        fw.close();
        reader.close();
    }
    
    public TopDocs applyFeedback(TRECQuery query, TopDocs topDocs) throws Exception {
        RelevanceModelIId fdbkModel;
                
        fdbkModel = new RelevanceModelConditional(this, query, topDocs);        
        try {
            fdbkModel.computeFdbkWeights();
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return topDocs;
        }
        
        if (Boolean.parseBoolean(prop.getProperty("clarity_compute", "false"))) {
            if (prop.getProperty("clarity.collmodel", "global").equals("global"))
                System.out.println("Clarity: " + fdbkModel.getQueryClarity(reader));
            else
                System.out.println("Clarity: " + fdbkModel.getQueryClarity());
        }

        postRLMQE = Boolean.parseBoolean(prop.getProperty("rlm.qe", "false"));
        TopDocs reranked = fdbkModel.rerankDocs();
        if (!postRLMQE)
            return reranked;
        
        // Post retrieval query expansion
        TRECQuery expandedQuery = fdbkModel.expandQuery();
        System.out.println("Expanded qry: " + expandedQuery.getLuceneQueryObj());
        
        // Reretrieve with expanded query
        topDocs = searcher.search(expandedQuery.getLuceneQueryObj(), numWanted);
        return topDocs;
    }
    
    public Evaluator evaluate() throws Exception {
        Evaluator evaluator = new Evaluator(this.getProperties());
        evaluator.load();
        evaluator.fillRelInfo();
        System.out.println(evaluator.computeAll());
        return evaluator;
    }

    public void saveRetrievedTuples(BufferedWriter bw, TRECQuery query, TopDocs topDocs) throws Exception {
        saveRetrievedTuples(bw, query, topDocs, null);
    }

    public void saveRetrievedTuples(BufferedWriter bw, TRECQuery query, TopDocs topDocs, Evaluator evaluator) throws Exception {
        PerQueryRelDocs perQueryRelDocs = null;
        int rel = 0;
        if (evaluator != null) {
            perQueryRelDocs = evaluator.getRelRcds().getRelInfo(query.id);
        }

        StringBuffer buff = new StringBuffer();
        ScoreDoc[] hits = topDocs.scoreDocs;

        for (int i = 0; i < hits.length; ++i) {
            int docId = hits[i].doc;
            Document d = searcher.doc(docId);
            String docName = d.get(TrecDocIndexer.FIELD_ID);

            if (perQueryRelDocs != null)
                rel = perQueryRelDocs.isRel(docName);

            buff.append(query.id.trim()).append("\tQ0\t").
                    append(d.get(TrecDocIndexer.FIELD_ID)).append("\t").
                    append((i+1)).append("\t").
                    append(rel).append("\t").
                    append(hits[i].score).append("\t").
                    append(runName).append("\n");                
        }

        bw.write(buff.toString());
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            args = new String[1];
            args[0] = "init.properties";
        }
        
        try {
            TrecDocRetriever searcher = new TrecDocRetriever(args[0], new LMJelinekMercerSimilarity(0.4f));            
            searcher.retrieveAll();            
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}

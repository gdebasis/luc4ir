/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.feedback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.luc4ir.indexing.TrecDocIndexer;
import org.luc4ir.trec.TRECQuery;
import org.luc4ir.retriever.TrecDocRetriever;

/**
 *
 * @author Debasis
 */

class KLDivScoreComparator implements Comparator<ScoreDoc> {

    @Override
    public int compare(ScoreDoc a, ScoreDoc b) {
        return a.score < b.score? -1 : a.score == b.score? 0 : 1;
    }    
}

public class RelevanceModelIId {
    TrecDocRetriever retriever;
    TRECQuery trecQuery;
    TopDocs topDocs;
    Properties prop;
    float mixingLambda;
    int numTopDocs;
    RetrievedDocsTermStats retrievedDocsTermStats;
    int nterms;
    float fbweight;
    IndexReader reader;
    PostFdbkReranker rerankerMethod;

    static final float TERM_SEL_DF_THRESH = 0.8f;
    
    public RelevanceModelIId(TrecDocRetriever retriever, TRECQuery trecQuery, TopDocs topDocs) throws Exception {
        this(retriever, trecQuery, topDocs, new KLDivReranker());
        this.prop = retriever.getProperties();
        this.retriever = retriever;
        this.trecQuery = trecQuery;
        this.topDocs = topDocs;
        numTopDocs = Integer.parseInt(prop.getProperty("fdbk.numtopdocs"));
        mixingLambda = Float.parseFloat(prop.getProperty("fdbk.lambda"));  
            
        nterms = Integer.parseInt(prop.getProperty("rlm.qe.nterms", "10"));
        fbweight = Float.parseFloat(prop.getProperty("rlm.qe.newterms.wt", "0.2"));
    }

    public RelevanceModelIId(TrecDocRetriever retriever, TRECQuery trecQuery, TopDocs topDocs, PostFdbkReranker rerankMethod) throws Exception {
        this.rerankerMethod = rerankMethod;
        this.prop = retriever.getProperties();
        this.retriever = retriever;
        this.trecQuery = trecQuery;
        this.topDocs = topDocs;
        numTopDocs = Integer.parseInt(prop.getProperty("fdbk.numtopdocs"));
        mixingLambda = Float.parseFloat(prop.getProperty("fdbk.lambda"));

        nterms = Integer.parseInt(prop.getProperty("rlm.qe.nterms", "10"));
        fbweight = Float.parseFloat(prop.getProperty("rlm.qe.newterms.wt", "0.2"));
    }

    public RetrievedDocsTermStats getRetrievedDocsTermStats() {
        return this.retrievedDocsTermStats;
    }
    
    public void buildTermStats() throws Exception {
        retrievedDocsTermStats = new
                RetrievedDocsTermStats(retriever.getReader(), topDocs, numTopDocs);
        retrievedDocsTermStats.buildAllStats();
        reader = retrievedDocsTermStats.getReader();
    }
    
    float mixTfIdf(RetrievedDocTermInfo w) {
        return mixingLambda*w.tf/(float)retrievedDocsTermStats.sumTf +
                (1-mixingLambda)*w.df/retrievedDocsTermStats.sumDf;        
    }    
    
    float mixTfIdf(RetrievedDocTermInfo w, PerDocTermVector docvec) {
        RetrievedDocTermInfo wGlobalInfo = retrievedDocsTermStats.termStats.get(w.getTerm());
        return mixingLambda*w.tf/(float)docvec.sum_tf +
                (1-mixingLambda)*wGlobalInfo.df/retrievedDocsTermStats.sumDf;        
    }
            
    public void computeFdbkWeights() throws Exception {
        float p_q;
        float p_w;
        
        buildTermStats();
        
        /* For each w \in V (vocab of top docs),
         * compute f(w) = \sum_{q \in qwvecs} K(w,q) */
        for (Map.Entry<String, RetrievedDocTermInfo> e : retrievedDocsTermStats.termStats.entrySet()) {
            float total_p_q = 0;
            RetrievedDocTermInfo w = e.getValue();
            p_w = mixTfIdf(w);
            
            Set<Term> qTerms = this.trecQuery.getQueryTerms(retriever.getSearcher());
            for (Term qTerm : qTerms) {
                
                // Get query term frequency
                RetrievedDocTermInfo qtermInfo = retrievedDocsTermStats.getTermStats(qTerm.toString());
                if (qtermInfo == null) {
                    System.err.println("No KDE for query term: " + qTerm.toString());
                    continue;
                }
                p_q = qtermInfo.tf/(float)retrievedDocsTermStats.sumTf; //mixTfIdf(qtermInfo); //
                
                total_p_q += Math.log(1+p_q);
            }
            w.wt = p_w * (float)Math.exp(total_p_q-1);
        }
    }
    
    public TopDocs rerankDocs() {
        rerankerMethod.setStats(topDocs, retrievedDocsTermStats);
        return rerankerMethod.rerankDocs();
    }
    
    public float getQueryClarity() {
        float klDiv = 0;
        float p_w_C;
        // For each v \in V (vocab of top ranked documents)
        for (Map.Entry<String, RetrievedDocTermInfo> e : retrievedDocsTermStats.termStats.entrySet()) {
            RetrievedDocTermInfo w = e.getValue();
            p_w_C = w.df/retrievedDocsTermStats.sumDf;
            klDiv += w.wt * Math.log(w.wt/p_w_C);
        }
        return klDiv;
    }
    
    public float getQueryClarity(IndexReader reader) throws Exception {
        float klDiv = 0;
        float p_w_C;
        // For each v \in V (vocab of top ranked documents)
        for (Map.Entry<String, RetrievedDocTermInfo> e : retrievedDocsTermStats.termStats.entrySet()) {
            RetrievedDocTermInfo w = e.getValue();
            double sumCf = (double)reader.getSumTotalTermFreq(TrecDocIndexer.FIELD_ANALYZED_CONTENT);
            double cf = reader.totalTermFreq(new Term(TrecDocIndexer.FIELD_ANALYZED_CONTENT, w.getTerm()));
            p_w_C = (float)(cf/sumCf);
            klDiv += w.wt * Math.log(w.wt/p_w_C);
        }
        return klDiv;
    }    
    
    // Implement post-RLM query expansion. Set the term weights
    // according to the values of f(w).
    public TRECQuery expandQuery() throws Exception {
        
        // The calling sequence has to make sure that the top docs are already
        // reranked by KL-div
        // Now reestimate relevance model on the reranked docs this time
        // for QE.
        computeFdbkWeights();
        
        TRECQuery expandedQuery = new TRECQuery(this.trecQuery);
        Set<Term> origTerms = new HashSet<>();
        this.trecQuery.luceneQuery
            .createWeight(retriever.getSearcher(), ScoreMode.COMPLETE, 1)
            .extractTerms(origTerms);
        HashMap<String, String> origQueryWordStrings = new HashMap<>();
        
        float normalizationFactor = 0;
        
        List<RetrievedDocTermInfo> termStats = new ArrayList<>();
        for (Map.Entry<String, RetrievedDocTermInfo> e : retrievedDocsTermStats.termStats.entrySet()) {
            RetrievedDocTermInfo w = e.getValue();
            w.wt = w.wt *
                    (float)Math.log(
                        reader.numDocs()/(float)
                        reader.docFreq(new Term(TrecDocIndexer.FIELD_ANALYZED_CONTENT, w.getTerm())));
            termStats.add(w);
            normalizationFactor += w.wt;
        }
        
        // Normalize the weights
        for (Map.Entry<String, RetrievedDocTermInfo> e : retrievedDocsTermStats.termStats.entrySet()) {
            RetrievedDocTermInfo w = e.getValue();
            w.wt = w.wt/normalizationFactor;
        }
        
        Collections.sort(termStats);

        BooleanQuery.Builder expandedQueryBuilder = new BooleanQuery.Builder();
        for (Term t : origTerms) {
            origQueryWordStrings.put(t.text(), t.text());
            //+++POST_SIGIR review: Assigned weights according to RLM post QE
            //tq.setBoost(1-fbweight);
            BoostQuery tq = new BoostQuery(
                    new TermQuery(t),
                    (1-fbweight)/(float)origTerms.size());
            //---POST_SIGIR review
            expandedQueryBuilder.add(tq, BooleanClause.Occur.SHOULD);
        }
        
        int nTermsAdded = 0;
        for (RetrievedDocTermInfo selTerm : termStats) {            
            String thisTerm = selTerm.getTerm();
            if (origQueryWordStrings.get(thisTerm) != null)
                continue;

            BoostQuery tq = new BoostQuery(
                new TermQuery(new Term(TrecDocIndexer.FIELD_ANALYZED_CONTENT, thisTerm)),
                fbweight*selTerm.wt
            );
            expandedQueryBuilder.add(tq, BooleanClause.Occur.SHOULD);
            
            //+++POST_SIGIR review: Assigned weights according to RLM post QE
            //tq.setBoost(fbweight);
            //tq.setBoost(selTerm.wt);
            //---POST_SIGIR review
            
            nTermsAdded++;
            if (nTermsAdded >= nterms)
                break;
        }
        
        return new TRECQuery(expandedQueryBuilder.build());
    }
        
}

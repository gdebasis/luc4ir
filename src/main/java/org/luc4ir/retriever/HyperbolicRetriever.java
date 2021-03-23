/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.retriever;

import java.io.IOException;
import java.util.*;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.luc4ir.trec.TRECQuery;

/**
 * Hyperbolic versions of standard retrieval models.
 * 
 * 
 * @author dganguly
 */

class TermWt {
    String term;
    float wt;

    public TermWt(String term, float wt) {
        this.term = term;
        this.wt = wt;
    }
}

/* Augmented vector with the time dimension */
class HypEmbVector implements Comparable<HypEmbVector> {
    int docId;     // identifies a document
    List<TermWt> x; // space-like components
    float t;  // the time-like component
    float k; // the curvature
    float querySim;  // used to rank the hyperbolic document vectors
    
    public HypEmbVector(int docId) {
        this.docId = docId;
        x = new ArrayList<>();
    }
    
    public HypEmbVector(Set<Term> qterms) {
        docId = -1; // < 0 denotes the query
        int numQTerms = qterms.size();
        x = new ArrayList<>(numQTerms);
        for (Term qterm: qterms) {
            x.add(new TermWt(qterm.text(), 1));
        }        
    }
    
    void add(Term t, float score) {
        String term = t.text();
        x.add(new TermWt(term, score));
    }
    
    float spaceInnerProduct(HypEmbVector another) {
        float inner_prod = 0;
        int p = x.size();
        
        for (int i=0; i < p; i++) {
            inner_prod += x.get(i).wt * another.x.get(i).wt;
        }
        return inner_prod;
    }
    
    // |x| = sqrt(<x.x>)
    float spaceNormSquared() { 
        return (float) spaceInnerProduct(this);
    }
    
    // Embed in the hyperboloid by computing the value of the 't' component
    void embed() {        
        float spaceNormSquared = spaceNormSquared();
        t = (float)Math.sqrt(1 + spaceNormSquared/(k*k));
    }
    
    float acosh(double x) {
        return (float)Math.log(x + Math.sqrt(x*x - 1.0));
    }    
    
    // arccosh (B(x,y)), where B(x,y) = x_0*y_0 - <inner product over space like coorodinates>
    float computeDistance(HypEmbVector another) {
        float bilinear = this.t * another.t - spaceInnerProduct(another);
        assert (bilinear > 0);
        return acosh(bilinear);
    }
    
    void setQuerySim(float dist) {
        querySim = (float)Math.exp(-dist);        
    }

    @Override
    public int compareTo(HypEmbVector o) {
        return Float.compare(o.querySim, querySim); // reverse order
    }
}

public class HyperbolicRetriever extends TrecDocRetriever {
    Map<Integer, HypEmbVector> topDocsMap;
    
    static final int INVLIST_PERTRERM_SIZE = 5000;

    public HyperbolicRetriever(String propFile, Similarity sim) throws Exception {
        super(propFile, sim);
        model = searcher.getSimilarity();
        topDocsMap = new HashMap<>();
    }
    
    void computeTermOverlapWeights(Term t) throws IOException {
        TermQuery tq = new TermQuery(t);
        TopDocs topDocs = searcher.search(tq, INVLIST_PERTRERM_SIZE);
        ScoreDoc[] scoreDocs = topDocs.scoreDocs;
        for (ScoreDoc sd: scoreDocs) {
            HypEmbVector embvec = topDocsMap.get(sd.doc);
            if (embvec == null) {
                embvec = new HypEmbVector(sd.doc);
                topDocsMap.put(sd.doc, embvec);
            }
            embvec.add(t, sd.score);
        }
    }
    
    // embed docs and query on a nhyperboloid
    HypEmbVector[] sortByHyperbolicDistances(Map<Integer, HypEmbVector> topDocsMap, Set<Term> qTerms) {
        
        HypEmbVector[] docvecsArray = new HypEmbVector[topDocsMap.values().size()];
        docvecsArray = topDocsMap.values().toArray(docvecsArray);
        
        HypEmbVector qvec = new HypEmbVector(qTerms);
        qvec.embed();
        
        for (HypEmbVector dvec: docvecsArray) {
            dvec.embed();
            float queryDist = dvec.computeDistance(qvec);
            dvec.setQuerySim(queryDist);
        }
        
        Arrays.sort(docvecsArray); // sort in descending order by query-similarities
        return docvecsArray;
    }
    
    @Override
    TopDocs retrieve(TRECQuery query) throws IOException {
        Query q = query.getLuceneQueryObj();
        Set<Term> qTerms = new HashSet<>();
        q.createWeight(this.searcher, ScoreMode.COMPLETE, 1).extractTerms(qTerms);

        // Accumulate the scores of a doc for each term 
        for (Term qTerm: qTerms) {
            computeTermOverlapWeights(qTerm);
        }
        
        HypEmbVector[] docvecs = sortByHyperbolicDistances(topDocsMap, qTerms);
        ScoreDoc[] rerankedSD = new ScoreDoc[numWanted];
        for (int i=0; i < numWanted; i++)
            rerankedSD[i] = new ScoreDoc(docvecs[i].docId, docvecs[i].querySim);
        
        TopDocs topdocs = new TopDocs(
                new TotalHits(numWanted, TotalHits.Relation.EQUAL_TO),
                rerankedSD);
        return topdocs;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            args = new String[1];
            args[0] = "init.properties";
        }
        
        try {
            HyperbolicRetriever searcher = new HyperbolicRetriever(args[0], new LMJelinekMercerSimilarity(0.4f));            
            searcher.retrieveAll();            
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }        
    }
}

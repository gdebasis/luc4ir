/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.retriever;

import java.io.IOException;
import java.util.*;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.luc4ir.feedback.RetrievedDocTermInfo;
import org.luc4ir.indexing.TrecDocIndexer;
import org.luc4ir.trec.TRECQuery;
import org.luc4ir.feedback.RetrievedDocsTermStats;
import org.luc4ir.feedback.PerDocTermVector;


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
    Map<String, TermWt> x; // space-like components
    float t;  // the time-like component
    float queryDist;  // used to rank the hyperbolic document vectors

    static final float K = 1.0f;  // the curvature

    public HypEmbVector(int docId) {
        this.docId = docId;
        x = new HashMap<>();
    }
    
    public HypEmbVector(Set<Term> qterms) {
        docId = -1; // < 0 denotes the query
        int numQTerms = qterms.size();
        x = new HashMap<>(numQTerms);
        for (Term qterm: qterms) {
            x.put(qterm.text(), new TermWt(qterm.text(), 1));
        }        
    }

    void add(String term, float score) {
        x.put(term, new TermWt(term, score));
    }

    static HypEmbVector plus(HypEmbVector a, HypEmbVector b) {
        return op(a, b, '+');
    }

    static HypEmbVector hadamard(HypEmbVector a, HypEmbVector b) {
        return op(a, b, '*');
    }

    static HypEmbVector op(HypEmbVector a, HypEmbVector b, char op) {
        HypEmbVector c = new HypEmbVector(a.docId);
        float r = 0;

        for (TermWt b_tw: b.x.values()) {
            TermWt a_tw = a.x.get(b_tw.term);
            if (a_tw != null) {
                r = op=='+'? a_tw.wt + b_tw.wt: a_tw.wt * b_tw.wt;
                c.x.put(a_tw.term, new TermWt(a_tw.term, r));
            }
        }
        return c;
    }

    static HypEmbVector scale(HypEmbVector a, float alpha) {
        HypEmbVector b = new HypEmbVector(a.docId);
        for (TermWt tw: a.x.values()) {
            b.x.put(tw.term, new TermWt(tw.term, tw.wt*alpha));
        }
        return b;
    }

    static HypEmbVector minus(HypEmbVector a, HypEmbVector b) {
        return plus(a, scale(b, -1));
    }

    float spaceInnerProduct(HypEmbVector another) {
        float inner_prod = 0;
        for (TermWt that_tw: another.x.values()) {
            TermWt tw = this.x.get(that_tw.term);
            if (tw != null) {
                inner_prod += tw.wt * that_tw.wt;
            }
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
        t = (float)Math.sqrt(1 + spaceNormSquared/(K*K));
    }

    float acosh(double x) {
        return (float)Math.log(x + Math.sqrt(x*x - 1.0));
    }    
    
    // arccosh (B(x,y)), where B(x,y) = x_0*y_0 - <inner product over space like coorodinates>
    float computeHyperbolicDistance(HypEmbVector another) {
        float bilinear = this.t*another.t - spaceInnerProduct(another);
        if (bilinear < 0) {
            System.err.println("B(x, y) <= 0");
            System.exit(1);
        }
        return acosh(bilinear);
    }
    
    void setQueryDist(float dist) {
        queryDist = dist;
    }

    @Override
    public int compareTo(HypEmbVector o) {
        return Float.compare(queryDist, o.queryDist);
    }
}

public class HyperbolicRetriever extends TrecDocRetriever {
    static final float LAMBDA = 0.4f;
    static final float ONE_MINUS_LAMBDA = 1.0f - LAMBDA;
    float NUMDOCS;

    List<HypEmbVector> docvecs = new ArrayList<>();
    RetrievedDocsTermStats retrievedDocsTermStats;

    public HyperbolicRetriever(String propFile, Similarity sim) throws Exception {
        super(propFile, sim);
        NUMDOCS = (float)reader.numDocs();
    }
    
    // embed docs and query on a hyperboloid; df_vec is the document frequency vector of query terms
    void sortByHyperbolicDistances(List<HypEmbVector> docvecs, HypEmbVector q_dfvec) {
        for (HypEmbVector dvec: docvecs) {
            float queryDist = dvec.computeHyperbolicDistance(q_dfvec);
            dvec.setQueryDist(queryDist);
        }
        Collections.sort(docvecs);
    }

    float lmWt(String term, PerDocTermVector dvec) throws IOException {
        Term t = new Term(TrecDocIndexer.FIELD_ANALYZED_CONTENT, term);
        return LAMBDA * dvec.getNormalizedTf(term) + ONE_MINUS_LAMBDA * (float)Math.log(NUMDOCS/reader.docFreq(t));
    }

    float lmWt(String term, int n) throws IOException {
        Term t = new Term(TrecDocIndexer.FIELD_ANALYZED_CONTENT, term);
        return LAMBDA * 1/(float)n + ONE_MINUS_LAMBDA * (float)Math.log(NUMDOCS/reader.docFreq(t));
    }

    
    @Override
    TopDocs retrieve(TRECQuery query) throws IOException {
        docvecs.clear();

        Query q = query.getLuceneQueryObj();

        Set<Term> qTerms = new HashSet<>();
        TopDocs topDocs = searcher.search(q, numWanted);

        ScoreDoc[] scoreDocs = topDocs.scoreDocs;
        HypEmbVector qvec = new HypEmbVector(-1); // dummy id

        q.createWeight(this.searcher, ScoreMode.COMPLETE, 1).extractTerms(qTerms);
        for (Term qTerm: qTerms) {
            qvec.add(qTerm.text(), lmWt(qTerm.text(), qTerms.size()));
        }
        qvec.embed();

        retrievedDocsTermStats = new RetrievedDocsTermStats(reader, topDocs, scoreDocs.length);
        int rank = 0;
        for (ScoreDoc sd: scoreDocs) {
            HypEmbVector hypdvec = new HypEmbVector(sd.doc);
            PerDocTermVector dvec = this.retrievedDocsTermStats.buildStatsForSingleDoc(sd.doc, ++rank, sd.score);

            for (RetrievedDocTermInfo termInfo: dvec.getPerDocStats().values()) {
                String term = termInfo.getTerm();
                hypdvec.add(term, lmWt(term, dvec)); // components for all terms
            }

            HypEmbVector delq = HypEmbVector.minus(hypdvec, qvec); // difference of this doc (LM-wt) with query

            delq.embed();  // difference vector of this doc with query
            docvecs.add(delq);
        }

        sortByHyperbolicDistances(docvecs, qvec);

        ScoreDoc[] rerankedSD = new ScoreDoc[topDocs.scoreDocs.length];
        int i=0;
        for (HypEmbVector dvec: docvecs) {
            rerankedSD[i] = new ScoreDoc(dvec.docId, dvec.queryDist);
            i++;
        }
        
        topDocs = new TopDocs(new TotalHits(numWanted, TotalHits.Relation.EQUAL_TO), rerankedSD);
        return topDocs;
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

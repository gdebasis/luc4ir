/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.evaluator;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.luc4ir.indexing.TrecDocIndexer;

/**
 *
 * @author debforit
 */
class TermFreq implements Comparable<TermFreq> {
    String term;
    float freq;

    TermFreq(String term) {
        this.term = term;
        freq = 0;
    }

    TermFreq(String term, float freq) {
        this.term = term;
        this.freq = freq;
    }

    @Override
    public int compareTo(TermFreq o) {
        return Float.compare(freq, o.freq);
    }

}

/**
 * Defines a sparse vector representation of a document in terms of a list
 * of (term, wt) pairs.
 * Useful for various utilities such as finding lengths, cosine similarities etc.
 *
 * @author dganguly
 */
public class DocVector {
    String text;
    HashMap<String, TermFreq> tfMap;

    private void init(String[] retrievedTerms, IndexReader reader) {
        tfMap = new HashMap<>();
        for (String term : retrievedTerms) {
            TermFreq tf = tfMap.get(term);
            if (tf == null) {
                tf = new TermFreq(term);
            }
            tf.freq++;
            tfMap.put(term, tf);
        }

        try {
            if (reader != null) {
                int N = reader.numDocs();
                for (Map.Entry<String, TermFreq> e : tfMap.entrySet()) {
                    TermFreq termWt = e.getValue();
                    int df = reader.docFreq(new Term(TrecDocIndexer.FIELD_ANALYZED_CONTENT, termWt.term));
                    float idf = (float) Math.log(N / (float) df);

                    termWt.freq = termWt.freq * idf;
                    //tfMap.put(termWt.term, new TermFreq(termWt.term, termWt.freq));
                }
            }
        }
        catch (IOException ex) { ex.printStackTrace(); }
    }

    public DocVector() {
        text = "";
        tfMap = new HashMap<>();
    }

    public DocVector(IndexReader reader, String text) {
        this(reader, text, 0);
    }

    public DocVector(IndexReader reader, String text, float topTermsFraction) {
        this(reader, text, 0);

        int numTermsToKeep = (int)(tfMap.size()*topTermsFraction);
        tfMap = tfMap
            .entrySet()
            .stream()
            .sorted(Comparator.comparing(e->e.getValue(), Comparator.reverseOrder()))
            .limit(numTermsToKeep)
            .collect(
                Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ))
        ;
    }

    public DocVector(String text, int ngramSize) {
        this(null, text, ngramSize);
    }

    public DocVector(String text) {
        this(null, text, 0);
    }

    public DocVector(IndexReader reader, String text, int ngramSize) {
        this.text = text;
        String[] retrievedTerms;

        try {
            if (ngramSize == 0)
                retrievedTerms = TrecDocIndexer.analyze(TrecDocIndexer.analyzer(), text).split("\\s+");
            else
                retrievedTerms = TrecDocIndexer.analyze(new NGramAnalyzer(ngramSize), text).split("\\s+");

            init(retrievedTerms, reader);
        }
        catch (IOException ex) { ex.printStackTrace(); }

    }

    public DocVector (DocVector that) {
        this.tfMap = new HashMap<>(that.tfMap);
        this.text = new String(that.text);
    }

    public void scale(float alpha) {
        for (Map.Entry<String, TermFreq> e: this.tfMap.entrySet()) {
            TermFreq termFreq = e.getValue();
            termFreq.freq *= alpha;
        }
    }

    static public DocVector computeCentroid(List<DocVector> vecs) {
        DocVector cvec = new DocVector();
        int numVecs = vecs.size();
        for (DocVector vec: vecs) {
            for (Map.Entry<String, TermFreq> e: vec.tfMap.entrySet()) {
                String term = e.getKey();
                float tf = e.getValue().freq;
                TermFreq cvec_tf = cvec.tfMap.get(term);
                if (cvec_tf == null) {
                    cvec_tf = new TermFreq(term);
                    cvec.tfMap.put(cvec_tf.term, cvec_tf);
                }
                cvec_tf.freq += tf;
            }
        }
        cvec.scale(1/(float)numVecs);
        return cvec;
    }
    
    public String getText() { return text; }

    float docLen() {
        float len = 0;
        for (TermFreq tf : tfMap.values()) {
            len += (tf.freq*tf.freq);
        }
        return (float)Math.sqrt(len);
    }
    
    /**
     * Computes similarity of this document object (bag of words)
     * with a list of query terms
     * @param queryTerms
     * @return 
     */
    public float cosineSimWithQuery(String[] queryTerms) {
        float sim = 0;
        float dLen = docLen();
        float qLen = (float)Math.sqrt(queryTerms.length);
        
        for (String q: queryTerms) {
            TermFreq tf = tfMap.get(q);
            if (tf == null)
                continue;
            sim += tf.freq;
        }
        return sim/(dLen*qLen);
    }
    
    /**
     * Computes similarity with another 'Document' object.
     * @param that
     * @return 
     */
    public float cosineSim(DocVector that) {
        float sim = 0;
        float dLen = docLen();
        float qLen = that.docLen();
        
        for (TermFreq tf: tfMap.values()) {
            TermFreq that_tf = that.tfMap.get(tf.term);
            if (that_tf == null)
                continue;
            sim += tf.freq * that_tf.freq;
        }
        return dLen==0 || qLen==0? 0 : sim/(dLen*qLen);
    }

    public float cosineSim(DocVector that, IndexReader reader) throws IOException {
        float sim = 0;
        float dLen = docLen();
        float qLen = that.docLen();
        int df;
        int N = reader.numDocs();
        double idf;

        for (TermFreq tf: tfMap.values()) {
            df = reader.docFreq(new Term(TrecDocIndexer.FIELD_ANALYZED_CONTENT, tf.term));
            idf = Math.log(N/df);
            idf = idf*idf;

            TermFreq that_tf = that.tfMap.get(tf.term);
            if (that_tf == null)
                continue;
            sim += tf.freq * that_tf.freq * idf;
        }
        return dLen==0 || qLen==0? 0 : sim/(dLen*qLen);
    }

    /**
     * Computes similarity with another 'Document' object.
     * @param that
     * @return 
     */
    public float jaccard(DocVector that) {
        Set<String> vocab = new HashSet(this.tfMap.keySet());
        vocab.addAll(that.tfMap.keySet()); // vocab is now the union
        
        Set <String> common = new HashSet(this.tfMap.keySet());        
        common.retainAll(that.tfMap.keySet());
        
        return common.size()/(float)vocab.size();
    }
    
    /**
     * Computes the (<a href="https://en.wikipedia.org/wiki/METEOR">METEOR</a>) score
     * between this document text and 'that' (parameter) Document object.
     * Used in the evaluation flow.
     * @param that
     * @return 
     */
    public float computeMETEOR(DocVector that) {
        float prec = computeBLEU(that);
        float recall = computeROUGE(that);
        float denom = prec + recall;
        return denom==0? 0 : 2*prec*recall/(prec+recall);
    }
    
    // this is the predicted and that is the reference
    public float computeBLEU(DocVector that) {
        int tp = 0, fp = 0;
        for (TermFreq tf: tfMap.values()) {
            TermFreq that_tf = that.tfMap.get(tf.term);
            if (that_tf == null) {
                // this is a false-positive error
                fp++;
            }
            else {
                tp++;
            }
        }
        float denom = tp+fp;
        return denom==0? 0 : tp/denom;
    }
    
    float computeROUGE(DocVector that) {
        int tp = 0, fn = 0;
        for (TermFreq tf: that.tfMap.values()) {
            TermFreq that_tf = this.tfMap.get(tf.term);
            if (that_tf == null) {
                // this is a false-negative error
                fn++;
            }
            else {
                tp++;
            }
        }
        float denom = tp+fn;
        return denom==0? 0 : tp/denom;
    }    
    
    /**
     * Returns a string representation of the set of terms and weights.
     * @return 
     */
    public String toString() {
        StringBuffer buff = new StringBuffer("[");
        
        for (TermFreq tf : this.tfMap.values()) {
            buff.append(tf.term).append(":").append(tf.freq).append(" ");
        }
        buff.append("]");
        
        return buff.toString();
    }    
}


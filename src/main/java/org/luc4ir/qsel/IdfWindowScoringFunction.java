/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.qsel;

import java.io.IOException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.luc4ir.indexing.TrecDocIndexer;

/**
 *
 * @author debforit
 */
public class IdfWindowScoringFunction implements WindowScoringFunction {
    
    @Override
    public float score(IndexReader reader, String[] tokensInWindow) {
        try {
            float idf, avgIdf = 0;
            int N = reader.numDocs();
            for (String token: tokensInWindow) {
                int df = reader.docFreq(new Term(TrecDocIndexer.FIELD_ANALYZED_CONTENT, token));
                idf = (float)Math.log(N/(float)df);
                avgIdf += idf;
            }

            return avgIdf/(float)tokensInWindow.length;
        }
        catch (IOException ex) { ex.printStackTrace(); }        
        return 0;
    }    
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.qsel;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.luc4ir.indexing.TrecDocIndexer;

/**
 *
 * @author debforit
 */
public interface WindowScoringFunction {
    
    public abstract float score(IndexReader reader, String[] tokensInWindow);
}

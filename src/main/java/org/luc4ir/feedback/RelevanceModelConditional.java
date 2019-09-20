/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.feedback;

import java.util.Map;
import org.apache.lucene.search.TopDocs;
import org.luc4ir.trec.TRECQuery;
import org.luc4ir.retriever.TrecDocRetriever;

/**
 *
 * @author Debasis
 */
public class RelevanceModelConditional extends RelevanceModelIId {

    public RelevanceModelConditional(TrecDocRetriever retriever, TRECQuery trecQuery, TopDocs topDocs) throws Exception {
        super(retriever, trecQuery, topDocs);
    }
    
    @Override
    public void computeFdbkWeights() throws Exception {
        float p_w;
        float this_wt; // phi(q,w)
        
        buildTermStats();
        
        int docsSeen = 0;

        // For each doc in top ranked
        for (PerDocTermVector docvec : this.retrievedDocsTermStats.docTermVecs) {            
            // For each word in this document
            for (Map.Entry<String, RetrievedDocTermInfo> e : docvec.perDocStats.entrySet()) {
                RetrievedDocTermInfo w = e.getValue();
                p_w = mixTfIdf(w, docvec);
                this_wt = p_w * docvec.sim/this.retrievedDocsTermStats.sumSim;
                
                // Take the average
                RetrievedDocTermInfo wGlobal = retrievedDocsTermStats.getTermStats(w.getTerm());
                wGlobal.wt += this_wt;      
            }
            docsSeen++;
            if (docsSeen >= numTopDocs)
                break;
        }  
    }
    
    
}

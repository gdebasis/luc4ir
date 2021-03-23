/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.retriever;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.luc4ir.trec.TRECQuery;
import org.luc4ir.trec.TRECQueryParser;
import org.luc4ir.qsel.IdfWindowScoringFunction;
import org.luc4ir.qsel.QuerySelector;
import org.luc4ir.qsel.WindowScoringFunction;

/**
 *
 * @author debforit
 */
public class VerboseQueryRetriever extends TrecDocRetriever {
    WindowScoringFunction wsf;
    int windowSize;

    public VerboseQueryRetriever(String propFile, Similarity sim, WindowScoringFunction wsf, int windowSize) throws Exception {
        super(propFile, sim);
        this.wsf = wsf;
        this.windowSize = windowSize;
    }
    
    @Override
    public List<TRECQuery> constructQueries() throws Exception {
        String queryFile = prop.getProperty("query.file");
        TRECQueryParser parser = new TRECQueryParser(queryFile, indexer.getAnalyzer());
        parser.parse();
        
        List<TRECQuery> queryList = parser.getQueries();
        List<TRECQuery> selectedTermQueries = new ArrayList<>();
        
        for (TRECQuery q: queryList) {
            QuerySelector qsel = new QuerySelector(q, reader, wsf, windowSize);
            TRECQuery sel_trecq = new TRECQuery(q.id, qsel.constructQuery());
            selectedTermQueries.add(sel_trecq);
        }
        
        return selectedTermQueries;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            args = new String[1];
            args[0] = "init.properties";
        }
        
        final int WSIZE = 3;
        
        try {
            VerboseQueryRetriever searcher = new VerboseQueryRetriever(args[0],
                    new LMJelinekMercerSimilarity(0.4f),
                    new IdfWindowScoringFunction(), WSIZE
            );
            
            searcher.retrieveAll();            
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }    
}

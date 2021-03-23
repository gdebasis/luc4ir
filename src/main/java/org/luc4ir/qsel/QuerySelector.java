/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.qsel;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.xml.builders.BooleanQueryBuilder;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.luc4ir.indexing.TrecDocIndexer;
import org.luc4ir.trec.TRECQuery;

/**
 *
 * @author debforit
 */

class WindowScore implements Comparable<WindowScore> {
    String[] tokens;
    float score;

    public WindowScore(String[] tokens, int id, float score) {
        this.tokens = tokens;
        this.score = score;
    }

    @Override
    public int compareTo(WindowScore that) {
        return Float.compare(score, that.score);
    }
}

public class QuerySelector {

    TRECQuery query;
    IndexReader reader;
    Analyzer analyzer;
    WindowScoringFunction wsf;
    int windowSize;
    
    public QuerySelector(TRECQuery query, IndexReader reader, WindowScoringFunction wsf, int windowSize) throws IOException {
        this.query = query;
        this.reader = reader;
        List<String> stopWords = FileUtils.readLines(new File("stop.txt"), StandardCharsets.UTF_8);
        analyzer = new EnglishAnalyzer(
            StopFilter.makeStopSet(stopWords)); // default analyzer
        
        this.wsf = wsf;
        this.windowSize = windowSize;
    }
    
    // select terms from the desc field
    public Query constructQuery() {
        String text = query.desc;
        String[] tokens = TrecDocIndexer.analyze(analyzer, text).split("\\s+");
        int start = 0;
        List<WindowScore> windowScores = new ArrayList<>();
        
        while (start < tokens.length) {
            int n = Math.min(windowSize, tokens.length-start);
            String[] span = new String[n];
            System.arraycopy(tokens, start, span, 0, n);
            float idfScore = wsf.score(reader, span);
            windowScores.add(new WindowScore(span, start, idfScore));
            start++;
        }
        
        // sort and take the last (max) scored one....
        Collections.sort(windowScores);
        WindowScore bestWindow = windowScores.get(windowScores.size()-1);
        
        // construct the Query object from the best window
        BooleanQuery.Builder selectedQueryBuilder = new BooleanQuery.Builder();
        for (String token: bestWindow.tokens) {
            TermQuery tq = new TermQuery(new Term(TrecDocIndexer.FIELD_ANALYZED_CONTENT, token));
            selectedQueryBuilder.add(new BooleanClause(tq, BooleanClause.Occur.SHOULD));
        }
        
        return (Query)selectedQueryBuilder.build();
    }
}

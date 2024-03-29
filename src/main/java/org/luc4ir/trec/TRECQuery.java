/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.trec;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.luc4ir.indexing.TrecDocIndexer;
import org.apache.lucene.analysis.Analyzer;


/**
 *
 * @author Debasis
 */
public class TRECQuery {
    public String       id;
    public String       title;
    public String       desc;
    public String       narr;
    public Query        luceneQuery;
    
    @Override
    public String toString() {
        return luceneQuery.toString();
    }

    public TRECQuery() {}

    public TRECQuery(Analyzer analyzer, String content, String id) {
        try {
            this.id = id;
            luceneQuery = new StandardQueryParser(analyzer).parse(
                    content, TrecDocIndexer.FIELD_ANALYZED_CONTENT);
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }

    public TRECQuery(Query luceneQuery) {
        this.luceneQuery = luceneQuery;
    }

    public TRECQuery(TRECQuery that) { // copy constructor
        this.id = that.id.trim();
        this.title = that.title;
        this.desc = that.desc;
        this.narr = that.narr;
    }
    
    public TRECQuery(String id, Query luceneQuery) {
        this.id = id.trim();
        this.title = "";
        this.desc = ""; this.narr = "";
        this.luceneQuery = luceneQuery;
    }

    public Query getLuceneQueryObj() { return luceneQuery; }

    public Set<Term> getQueryTerms(IndexSearcher searcher) throws IOException {
        Set<Term> terms = new HashSet<>();
        luceneQuery.createWeight(searcher, ScoreMode.COMPLETE, 1).extractTerms(terms);
        return terms;
    }
}

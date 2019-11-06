/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.trec;

import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;

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
    
    public TRECQuery(TRECQuery that) { // copy constructor
        this.id = that.id;
        this.title = that.title;
        this.desc = that.desc;
        this.narr = that.narr;
    }

    public Query getLuceneQueryObj() { return luceneQuery; }

    public Set<Term> getQueryTerms() {
        Set<Term> terms = new HashSet<>();
       // Weight w = searcher.createWeight(true, 3.4f);
        //w.extractTerms(terms);
        String st[] = luceneQuery.toString().split("\\s+");
        for(String s: st){
            terms.add(new Term("words", s));
        }
        //luceneQuery.extractTerms(terms);
        return terms;
    }
}

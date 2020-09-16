/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.retriever;

import java.io.File;
import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.luc4ir.indexing.TrecDocIndexer;

/**
 *
 * @author debforit
 */
public class DocViewer {
    IndexReader reader;
    IndexSearcher searcher;
    
    public DocViewer(String indexDirPath) throws IOException {
        File indexDir = new File(indexDirPath);        
        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
        searcher = new IndexSearcher(reader);
    }

    public String getDocText(String docId) throws Exception {
        TermQuery tq = new TermQuery(new Term(TrecDocIndexer.FIELD_ID, docId.trim()));
        TopDocs topDocs = searcher.search(tq, 1);
        if (topDocs.scoreDocs.length <= 0)
            return null;
                    
        Document d = reader.document(topDocs.scoreDocs[0].doc);
        return d.get(TrecDocIndexer.FIELD_ANALYZED_CONTENT);
    }
    
    public static void main(String[] args) {
        
    }
}

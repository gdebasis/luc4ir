/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.indexing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.FSDirectory;

/**
 *
 * @author Debasis
 */
public class IndexHtmlToText {
        
    static String getHTMLFromDocId(String indexDirPath, String docId) throws Exception {
        IndexReader reader;
        IndexSearcher searcher;
        
        File indexDir = new File(indexDirPath);        
        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
        searcher = new IndexSearcher(reader);
        
        TopScoreDocCollector collector;
        TopDocs topDocs;
        
        Query query = new TermQuery(new Term(TrecDocIndexer.FIELD_ID, docId));
        topDocs = searcher.search(query, 1);
        ScoreDoc sd = topDocs.scoreDocs[0];
                
        Document doc = reader.document(sd.doc);
        String htmlDecompressed = decompress(doc.getBinaryValue(TrecDocIndexer.FIELD_ANALYZED_CONTENT).bytes);
        System.out.println(htmlDecompressed);
        
        reader.close();
        return htmlDecompressed;
    }
    
    static String decompress(byte[] bytes) {
        try {
            InputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[262144];  // about 300kb
            int len;
            while((len = in.read(buffer))>0)
                baos.write(buffer, 0, len);
            return new String(baos.toByteArray(), "UTF-8");
        }
        catch (Exception e) {
            return "";
        }
    }    
    
    public static void main(String[] args) {
        try {
            IndexHtmlToText.getHTMLFromDocId(
                    "C:/research/corpora/wt10g_subset/index/",
                    "WTX001-B01-1");
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
}

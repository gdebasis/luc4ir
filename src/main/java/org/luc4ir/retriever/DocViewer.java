/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.retriever;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.james.mime4j.Charsets;
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
    String idFieldName, contentFieldName;
    
    public DocViewer(String indexDirPath, String docFieldName, String contentFieldName) throws IOException {
        File indexDir = new File(indexDirPath);        
        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
        searcher = new IndexSearcher(reader);
        this.idFieldName = docFieldName;
        this.contentFieldName = contentFieldName;
    }
    
    public DocViewer(String indexDirPath) throws IOException {
        this(indexDirPath, TrecDocIndexer.FIELD_ID, TrecDocIndexer.FIELD_ANALYZED_CONTENT);
    }

    public String getDocText(String docId) throws Exception {
        TermQuery tq = new TermQuery(new Term(idFieldName, docId.trim()));
        TopDocs topDocs = searcher.search(tq, 1);
        if (topDocs.scoreDocs.length <= 0)
            return null;
        
        Document d = reader.document(topDocs.scoreDocs[0].doc);
        return d.get(contentFieldName).replace("\n", " ").replace("\r", " ");
    }
    
    void close() throws Exception {
        reader.close();
    }
    
    public static void main(String[] args) {
        // Read a file of <qids> <docids> (useful for a pool) and display the text
        if (args.length < 4) {
            System.err.println("usage: java DocViewer <two column id file - first vol - qid, sedond docid> <indexdir> <id-field name> <content field name>");
            System.err.println("Evaluating on sample TREC docids");            
            
            // else demo on the sample provide...
            args = new String[2];
            args[0] = "sample.res";
            args[1] = "index_trecd45";  // this would only be present after u run the index
        }
        
        try {
            List<String> lines = FileUtils.readLines(new File(args[0]), Charsets.UTF_8);
            DocViewer dv = args.length>2? new DocViewer(args[1], args[2], args[3]): new DocViewer(args[1]);
            
            for (String line: lines) {
                String[] tokens = line.split("\\s+");
                String text = dv.getDocText(tokens[1]);
                System.out.println(String.format("%s\t%s\t%s", tokens[0], tokens[1], text));
            }
            dv.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        
    }
}

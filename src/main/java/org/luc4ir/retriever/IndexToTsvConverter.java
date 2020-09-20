/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.retriever;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;
import org.luc4ir.indexing.TrecDocIndexer;

/**
 *
 * @author debforit
 */
public class IndexToTsvConverter {
    
    IndexReader reader;
    String outTsvFile;
    
    public IndexToTsvConverter(String indexDirPath, String outTsvFile) throws IOException {
        File indexDir = new File(indexDirPath);        
        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
        this.outTsvFile = outTsvFile;
    }

    public void convert() throws Exception {
        FileWriter fw = new FileWriter(outTsvFile);
        BufferedWriter bw = new BufferedWriter(fw);
        StringBuffer buff = new StringBuffer();
        
        int numDocs = reader.numDocs();
        
        for (int i=0; i < numDocs; i++) {
            Document d = reader.document(i);
            
            buff.append(d.get(TrecDocIndexer.FIELD_ID)).append("\t");
            buff.append(d.get(TrecDocIndexer.FIELD_ANALYZED_CONTENT));
            
            String text = buff.toString().replace("\n", "").replace("\r", "");            
            bw.write(text);
            
            bw.newLine();
            buff.setLength(0);
        }
        bw.close();
        fw.close();
        reader.close();
    }
    
    public static void main(String[] args) {
        // Read a file of <qids> <docids> (useful for a pool) and display the text
        if (args.length < 2) {
            System.err.println("usage: java IndexToTsvConverter <indexdir> <tsvfile>");
            System.err.println("Evaluating on sample TREC docids");            
            
            // else demo on the sample provide...
            args = new String[2];
            args[0] = "index_trecd45";  // ths would only be present after u run the index
            args[1] = "trecd45.tsv";  // ths would only be present after u run the index
        }
        
        try {
            new IndexToTsvConverter(args[0], args[1]).convert();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        
    }
}


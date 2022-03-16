package org.luc4ir.indexing;

import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.document.Document;

import java.io.File;

public class IndexTester {
    public static void main(String[] args) throws Exception {
        TrecDocIndexer indexer;
        IndexReader reader;

        indexer = new TrecDocIndexer("init.properties");
        File indexDir = indexer.getIndexDir();
        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));

        Document d = reader.document(0);
        System.out.println("Fields stored in the index...");
        for (IndexableField f: d.getFields()) {
            System.out.println(f.name());
        }
        Terms tfs = reader.getTermVector(0, TrecDocIndexer.FIELD_ANALYZED_CONTENT);
        if (tfs == null)
            System.out.println("Term vectors not indexed");
        else
            System.out.println("Vector size: " + tfs.size());

        String[] word2Tests = {"architecture", "above", "computers" };
        for (String word2Test: word2Tests) {
            word2Test = TrecDocIndexer.analyze(indexer.getAnalyzer(), word2Test);
            if (word2Test.length() > 0) {
                int df = reader.docFreq(new Term(TrecDocIndexer.FIELD_ANALYZED_CONTENT, word2Test));
                System.out.println(String.format("DF(%s): %d", word2Test, df));
            }
        }
    }
}

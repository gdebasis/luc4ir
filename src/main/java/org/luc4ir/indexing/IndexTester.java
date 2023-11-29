package org.luc4ir.indexing;

import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.document.Document;
import org.apache.lucene.util.BytesRef;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class IndexTester {

    static void showTokensForField(IndexReader reader, String fieldName, String fileName) throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter(fileName));
        List<LeafReaderContext> list = reader.leaves();
        int count = 0;
        for (LeafReaderContext lrc : list) {
            Terms terms = lrc.reader().terms(fieldName);
            if (terms != null) {
                TermsEnum termsEnum = terms.iterator();

                BytesRef term;
                while ((term = termsEnum.next()) != null) {
                    bw.write(term.utf8ToString());
                    bw.newLine();
                    count++;
                }
            }
        }
        bw.close();
        System.out.println(count + " terms found in the index.");
    }

    public static void main(String[] args) throws Exception {
        TrecDocIndexer indexer;
        IndexReader reader;

        //indexer = new TrecDocIndexer("msmarco/index.msmarco.properties");
        indexer = new TrecDocIndexer("retrieve.properties");
        File indexDir = indexer.getIndexDir();
        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));

        System.out.println("#docs in the index: " + reader.numDocs());

        showTokensForField(reader, TrecDocIndexer.FIELD_ANALYZED_CONTENT, "vocab.trec.txt");

        /*
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
         */

        reader.close();
    }
}

package org.luc4ir.genutils;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.luc4ir.indexing.TrecDocIndexer;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Destemmer {
    IndexReader reader;
    Set<String>[] vocab; // mapped by starting prefix

    Destemmer(IndexReader reader) {
        this.reader = reader;
        vocab = new Set[26];

        try {
            loadTerms();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private boolean isValid(String word) {
        for (int i=0; i < word.length(); i++) {
            char ch = word.charAt(i);
            if (!(ch >= 'a' && ch <= 'z'))
                return false;
        }
        return true;
    }

    private void loadTerms() throws Exception {
        String word;
        int numDocs = reader.numDocs();

        for (int i=0; i < numDocs; i++) {
            String str = reader.document(i).get(TrecDocIndexer.FIELD_ANALYZED_CONTENT);
            String[] tokens = TrecDocIndexer.analyze(new StandardAnalyzer(), str).split(" ");

            for (String token: tokens) {
                if (isValid(token))
                    addTerm(token);
            }
        }
    }

    private void addTerm(String word) {
        int start = word.charAt(0) - 'a';
        if (vocab[start] == null)
            vocab[start] = new TreeSet<String>();

        vocab[start].add(word);
    }

    public String destem(String key) {
        int start = key.charAt(0) - 'a';
        if (vocab[start] == null)
            return null;

        for (String e: vocab[start]) {
            if (e.startsWith(key))
                return e;
        }
        return null;
    }

    public static void main(String[] args) {
        TrecDocIndexer indexer;
        IndexReader reader;

        try {
            indexer = new TrecDocIndexer("init.properties");
            File indexDir = indexer.getIndexDir();
            reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));

            String[] word2Tests = {"babi", "computers", "veri" };
            System.out.println("Loading vocabulary....");
            Destemmer destemmer = new Destemmer(reader);

            for (String key: word2Tests) {
                String destem = destemmer.destem(key);
                System.out.println(destem);
            }

            reader.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

    }

}

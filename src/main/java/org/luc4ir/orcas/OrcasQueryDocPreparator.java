package org.luc4ir.orcas;

// Reads the orcas.tsv file which contains query docid mapping
// Uses the MSMARCO document index to fetch the document content
// Output query id: query text: doc text-1: doc-text2 and so on.

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.luc4ir.indexing.TrecDocIndexer;

import java.io.*;

public class OrcasQueryDocPreparator {

    String getDocContent(IndexSearcher searcher, String docId) throws IOException {
        Query query = new TermQuery(new Term(TrecDocIndexer.FIELD_ID, docId));
        TopDocs topDocs = searcher.search(query, 1);
        if (topDocs.scoreDocs.length == 0) return null;

        ScoreDoc sd = topDocs.scoreDocs[0];
        Document doc = searcher.getIndexReader().document(sd.doc);
        return doc.get(TrecDocIndexer.FIELD_ANALYZED_CONTENT);
    }

    void processAll(String tsvFile, String indexPath) throws Exception {
        String line, content;
        int count = 0;
        final String outFile = "orcas.reldocs.tsv";

        File indexDir = new File(indexPath);
        System.out.println("Running queries against index: " + indexDir.getPath());

        IndexReader reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
        IndexSearcher searcher = new IndexSearcher(reader);

        try (   BufferedReader br = new BufferedReader(new FileReader(tsvFile));
                BufferedWriter bw = new BufferedWriter(new FileWriter(outFile))) {
            while ((line = br.readLine()) != null) {
                line = line.trim();
                content = getDocContent(searcher, line);
                if (content == null)
                    continue;

                bw.write(line);
                bw.write("\t");
                bw.write(content);
                bw.newLine();

                count++;
                if (count%1000==0)
                    System.out.print(String.format("Processed %d queries\r", count));
            }
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: java OrcasQueryDocPreparator <orcas_reldocs> <MSMARCO doc index>");
            return;
        }
        final String dataFile = args[0];
        final String indexPath = args[1];

        try {
            new OrcasQueryDocPreparator().processAll(dataFile, indexPath);
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
}

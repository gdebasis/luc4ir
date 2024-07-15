package org.luc4ir.retriever;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import org.luc4ir.evaluator.AllRelRcds;
import org.luc4ir.evaluator.PerQueryRelDocs;
import org.luc4ir.indexing.TrecDocIndexer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ToucheQrelsBuilder {

    static Boolean getMetadata(IndexSearcher searcher, String docId) throws IOException {
        Query query = new TermQuery(new Term(TrecDocIndexer.FIELD_ID, docId));
        TopDocs topDocs = searcher.search(query, 1);
        if (topDocs.scoreDocs.length == 0) return null;

        ScoreDoc sd = topDocs.scoreDocs[0];
        Document doc = searcher.getIndexReader().document(sd.doc);
        return doc.get(TrecDocIndexer.FIELD_METADATA).equalsIgnoreCase("pro")? true: false;
    }

    public static void main(String[] args) throws Exception {
        TrecDocIndexer indexer;
        IndexReader reader;
        AllRelRcds relRcds;

        indexer = new TrecDocIndexer("touche.properties");
        File indexDir = indexer.getIndexDir();
        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new BM25Similarity());

        String qrelsFile = indexer.getProperties().getProperty("qrels.file");
        relRcds = new AllRelRcds(qrelsFile);
        relRcds.load();

        HashMap<String, PerQueryRelDocs> relInfo = relRcds.getPerQueryRels();

        BufferedWriter bw_aug = new BufferedWriter(new FileWriter(qrelsFile + ".stanceinfo"));
        for (Map.Entry<String, PerQueryRelDocs> e: relInfo.entrySet()) {
            String qid = e.getKey(); // a query

            PerQueryRelDocs relInfoQ = e.getValue();
            HashMap<String, Float> relMap = relInfoQ.getRelDocs();
            for (String qRelDocName: relMap.keySet()) {
                Boolean proOrCon_Q = getMetadata(searcher, qRelDocName);
                if (proOrCon_Q == null)
                    continue;
                String pro_or_con = proOrCon_Q? "pro": "con";
                bw_aug.write(String.format("%s\tQ0\t%s\t%d\t%s\n", qid, qRelDocName,
                        (int)(relMap.get(qRelDocName).floatValue()), pro_or_con));
            }
        }
        bw_aug.close();

        BufferedWriter bw_constrained = new BufferedWriter(new FileWriter(qrelsFile + ".constrained"));

        for (Map.Entry<String, PerQueryRelDocs> e: relInfo.entrySet()) {
            String qid = e.getKey(); // a query

            PerQueryRelDocs relInfoQ = e.getValue();
            HashMap<String, Float> relMap = relInfoQ.getRelDocs();
            for (String qRelDocName: relMap.keySet()) {
                Boolean proOrCon_Q = getMetadata(searcher, qRelDocName);
                if (proOrCon_Q == null)
                    continue;

                for (String relDocName : relMap.keySet()) {
                    Boolean proOrCon = getMetadata(searcher, relDocName); // query arg stance
                    if (proOrCon == null)
                        continue;
                    //System.out.println(String.format("Query: %s <%s %s> <%s %s>", qid, qRelDocName, proOrCon_Q, relDocName, proOrCon));
                    if (proOrCon.booleanValue() != proOrCon_Q.booleanValue()) // only of the opposite polarity is relevant to us!
                        bw_constrained.write(String.format("%s|%s\tQ0\t%s\t%d\n", qid, qRelDocName,
                            relDocName, (int) (relMap.get(relDocName).floatValue())));
                }
            }
        }
        bw_constrained.close();
    }
}

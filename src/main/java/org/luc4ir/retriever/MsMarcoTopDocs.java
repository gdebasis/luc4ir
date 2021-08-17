package org.luc4ir.retriever;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.luc4ir.evaluator.Evaluator;
import org.luc4ir.indexing.TrecDocIndexer;
import org.luc4ir.trec.TRECQuery;
import org.luc4ir.trec.TRECQueryParser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.Charset;
import java.util.*;

public class MsMarcoTopDocs extends TrecDocRetriever {

    public MsMarcoTopDocs(String propFile, Similarity sim) throws Exception {
        super(propFile, sim);
        numWanted = 1;
    }

    @Override
    public List<TRECQuery> constructQueries() throws Exception {
        final String queryFile = "orcas/queries.txt"; // qid "\t" query
        List<String> lines = FileUtils.readLines(new File(queryFile), Charset.defaultCharset());
        List<TRECQuery> trecFmtQueries = new ArrayList<>(lines.size());
        for (String line: lines) {
            String[] parts = line.split("\t");
            trecFmtQueries.add(new TRECQuery(indexer.getAnalyzer(), parts[1], parts[0]));
        }
        return trecFmtQueries;
    }


    public void retrieveAll() throws Exception {
        TopDocs topDocs;
        Map<String, TopDocs> topDocsMap = new HashMap<>();
        int docId;

        BufferedWriter bw = new BufferedWriter(new FileWriter("orcas/qid_topdoc.txt"));

        List<TRECQuery> queries = constructQueries();
        for (TRECQuery query : queries) {
            // Retrieve results
            topDocs = retrieve(query);
            if (topDocs.scoreDocs.length == 0)
                continue;

            System.out.print("Writing topdoc info for query " + query.id + "\r");
            bw.write(query.id);
            bw.write("\t");
            bw.write(reader.document(topDocs.scoreDocs[0].doc).get(TrecDocIndexer.FIELD_ANALYZED_CONTENT));
            bw.newLine();
        }

        bw.close();
        reader.close();
    }

    void genIDFData(String vocabFile, String outFile) throws Exception {  // read the vocab.txt file and write out log(N/df(t))
        List<String> lines = FileUtils.readLines(new File(vocabFile), Charset.defaultCharset());
        BufferedWriter bw = new BufferedWriter(new FileWriter(outFile));
        int N = reader.numDocs();
        for (String line: lines) {
            String word = TrecDocIndexer.analyze(indexer.getAnalyzer(), line).trim();
            int df = reader.docFreq(new Term(TrecDocIndexer.FIELD_ANALYZED_CONTENT, word));
            bw.write(line.trim() + "\t");
            bw.write(String.valueOf(Math.log(N/(double)df)));
            bw.newLine();
        }
        bw.close();
    }

    public static void main(String[] args) {
        try {
            MsMarcoTopDocs msMarcoTopDocs =
                    new MsMarcoTopDocs("msmarco/index.msmarco.properties",
                    new LMJelinekMercerSimilarity(0.6f));

            msMarcoTopDocs.genIDFData("orcas/vocab.txt", "orcas/word_idf.txt");
            msMarcoTopDocs.retrieveAll();
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
}

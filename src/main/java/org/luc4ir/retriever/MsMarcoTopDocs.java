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
    }

    @Override
    public List<TRECQuery> constructQueries() throws Exception {
        final String queryFile = prop.getProperty("query.file"); // qid "\t" query
        List<String> lines = FileUtils.readLines(new File(queryFile), Charset.defaultCharset());
        List<TRECQuery> trecFmtQueries = new ArrayList<>(lines.size());
        for (String line: lines) {
            String[] parts = line.split("\t");
            TRECQuery q = new TRECQuery(indexer.getAnalyzer(), parts[1], parts[0]);
            trecFmtQueries.add(q);
        }
        return trecFmtQueries;
    }


    public void retrieveAll() throws Exception {
        TopDocs topDocs;

        BufferedWriter bw = new BufferedWriter(new FileWriter(prop.getProperty("res.file")));

        List<TRECQuery> queries = constructQueries();
        for (TRECQuery query : queries) {
            // Retrieve results
            topDocs = retrieve(query);
            saveRetrievedTuples(bw, query, topDocs);
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
                    //new MsMarcoTopDocs("msmarco/index.msmarco.properties",
                    new MsMarcoTopDocs("touche.properties",
                    new LMJelinekMercerSimilarity(0.6f)
            );

            //System.out.println(msMarcoTopDocs.reader.numDocs());

            //msMarcoTopDocs.genIDFData("orcas/vocab.txt", "orcas/word_idf.txt");
            msMarcoTopDocs.retrieveAll();
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
}

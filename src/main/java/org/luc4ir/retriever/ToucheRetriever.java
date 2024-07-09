package org.luc4ir.retriever;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.luc4ir.evaluator.AllRelRcds;
import org.luc4ir.evaluator.PerQueryRelDocs;
import org.luc4ir.indexing.TrecDocIndexer;
import org.luc4ir.qsel.IdfWindowScoringFunction;
import org.luc4ir.qsel.QuerySelector;
import org.luc4ir.trec.TRECQuery;
import ucar.nc2.util.IO;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToucheRetriever extends MsMarcoTopDocs {
    AllRelRcds relRcds;
    final int NUM_WINDOWS = 1;

    public ToucheRetriever(String propFile, Similarity sim) throws Exception {
        super(propFile, sim);
        String qrelsFile = this.getProperties().getProperty("qrels.file");

        relRcds = new AllRelRcds(qrelsFile);
        relRcds.load();

        System.out.println(String.format("Loaded %d records from %s", relRcds.getPerQueryRels().keySet().size(), qrelsFile));
    }

    Document getRelDoc(String docName) throws IOException {
        Query query = new TermQuery(new Term(TrecDocIndexer.FIELD_ID, docName));
        TopDocs topDocs = searcher.search(query, 1);
        if (topDocs.scoreDocs.length == 0) return null;

        ScoreDoc sd = topDocs.scoreDocs[0];
        Document doc = searcher.getIndexReader().document(sd.doc);
        return doc;
    }

    Query extractQueryFromDoc(String queryText, String docText) throws IOException {
        QuerySelector qsel = new QuerySelector(reader, this.indexer.getAnalyzer(),
                new IdfWindowScoringFunction(), 3);
        return qsel.constructQuery(queryText, docText, NUM_WINDOWS);
    }

    private void retrieveForArguments(BufferedWriter bw, TRECQuery query, AllRelRcds relRcds) throws Exception {
        String qid = query.id;
        HashMap<String, Float> relDocMap= relRcds.getRelInfo(qid).getRelDocs();

        for (String docName: relDocMap.keySet()) {
            Document relDoc = getRelDoc(docName);
            String docText = relDoc.get(TrecDocIndexer.FIELD_ANALYZED_CONTENT);
            //System.out.println(String.format("Topic + Arg %s:\nTopic: %s\nRel doc: %s", query.id, query.title, docText));
            Query queryWithArg = extractQueryFromDoc(query.title, docText);
            System.out.println(String.format("Retrieving with query: %s", queryWithArg));
            TopDocs topDocs = searcher.search(queryWithArg, numWanted);
            saveRetrievedTuples(bw, query.id + "|" + docName, topDocs);
        }
    }

    @Override
    public List<TRECQuery> constructQueries() throws Exception {
        final String queryFile = prop.getProperty("query.file"); // qid "\t" query
        List<String> lines = FileUtils.readLines(new File(queryFile), Charset.defaultCharset());
        JSONParser parser = new JSONParser();
        String desc = "";
        List<TRECQuery> trecFmtQueries = new ArrayList<>(lines.size());

        for (String line: lines) {
            JSONObject jsonLine = (JSONObject)parser.parse(new StringReader(line));
            String id = jsonLine.get("_id").toString();
            String title = jsonLine.get("text").toString();
            String metadata = jsonLine.get("metadata").toString();
            if (prop.getProperty("query.fields").equalsIgnoreCase("td"))
                desc = ((JSONObject)parser.parse(metadata)).get("description").toString();

            TRECQuery q = new TRECQuery(indexer.getAnalyzer(), title + " " + desc, id);
            trecFmtQueries.add(q);
        }
        return trecFmtQueries;
    }

    public void retrieveAll(List<TRECQuery> queries) throws Exception {
        boolean constrained = Boolean.parseBoolean(prop.getProperty("retrieval.constrained", "false"));
        String suffix = !constrained? "": ".constrained";
        String resFile = prop.getProperty("res.file") + suffix;

        BufferedWriter bw = new BufferedWriter(new FileWriter(resFile));
        System.out.println("Saving results to: " + resFile);

        for (TRECQuery query : queries) {
            if (constrained)
                retrieveForArguments(bw, query, relRcds);
            else {
                TopDocs topDocs = retrieve(query);
                saveRetrievedTuples(bw, query, topDocs);
            }
        }
        bw.close();
    }

    public static void main(String[] args) {
        try {
            ToucheRetriever toucheRetriever =
                    new ToucheRetriever("touche.properties",
                        new BM25Similarity()
                    );

            List<TRECQuery> queries = toucheRetriever.constructQueries();
            toucheRetriever.retrieveAll(queries);

            toucheRetriever.reader.close();
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
}

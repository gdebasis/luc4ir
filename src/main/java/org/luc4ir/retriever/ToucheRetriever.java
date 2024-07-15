package org.luc4ir.retriever;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.Similarity;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.luc4ir.evaluator.AllRelRcds;
import org.luc4ir.evaluator.DocVector;
import org.luc4ir.evaluator.PerQueryRelDocs;
import org.luc4ir.genutils.ScoreDocUtils;
import org.luc4ir.indexing.TrecDocIndexer;
import org.luc4ir.qsel.IdfWindowScoringFunction;
import org.luc4ir.qsel.QuerySelector;
import org.luc4ir.trec.TRECQuery;
import ucar.nc2.util.IO;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

public class ToucheRetriever extends MsMarcoTopDocs {
    AllRelRcds relRcds;
    int numWindows;
    int windowSize;

    public ToucheRetriever(String propFile, Similarity sim) throws Exception {
        super(propFile, sim);
        numWindows = Integer.parseInt(prop.getProperty("retrieval.constrained.numwindows", "5"));
        windowSize = Integer.parseInt(prop.getProperty("retrieval.constrained.wsize", "3"));

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

    Query constructQuery(String queryText) {
        String[] queryTextTokens = TrecDocIndexer.analyze(this.indexer.getAnalyzer(), queryText).split("\\s+");
        BooleanQuery.Builder qb = new BooleanQuery.Builder();
        // The original query terms
        for (String token : queryTextTokens) {
            TermQuery tq = new TermQuery(new Term(TrecDocIndexer.FIELD_ANALYZED_CONTENT, token));
            qb.add(new BooleanClause(tq, BooleanClause.Occur.SHOULD));
        }
        return qb.build();
    }

    TopDocs retrieveConstrained(String queryText, String docText) throws IOException {
        TopDocs topDocs = null;
        Query topicQuery = constructQuery(queryText);
        QuerySelector qsel = new QuerySelector(reader, this.indexer.getAnalyzer(),
                new IdfWindowScoringFunction(), windowSize);

        //System.out.println(String.format("Retrieving with query: %s", topicQuery));
        TopDocs topDocs_topicQuery = searcher.search(topicQuery, numWanted);

        if (numWindows == 0)
            return topDocs_topicQuery;

        Query argQuery = qsel.constructQuery(docText, numWindows);

        //System.out.println(String.format("Retrieving with query: %s", argQuery));
        TopDocs topDocs_argQuery = searcher.search(argQuery, numWanted);

        /*
        // RR fusion
        topDocs = new RRFusion().combine(
                topDocs_topicQuery.scoreDocs,
                topDocs_argQuery.scoreDocs,
                numWanted)
        ;
         */
        return topDocs_argQuery;
    }

    List<DocVector> topDocVecs(TopDocs topDocs) throws IOException {
        List<DocVector> dvecs = new ArrayList<>();
        for (ScoreDoc sd: topDocs.scoreDocs) {
            Document d = reader.document(sd.doc);
            DocVector dv = new DocVector(d.get(TrecDocIndexer.FIELD_ANALYZED_CONTENT));
            dvecs.add(dv);
        }
        return dvecs;
    }

    float proConRatio(Set<String> docNames) throws IOException {
        int numPro = 0;
        for (String docName: docNames) {
            boolean pro = ToucheQrelsBuilder.getMetadata(searcher, docName);
            if (pro)
                numPro++;
        }
        return numPro/(float)(docNames.size());
    }

    float computeProConRatio(String qid, TopDocs topDocs) throws IOException {
        Set<String> relDocNames = relRcds.getRelInfo(qid).getRelDocs().keySet();
        Set<String> retDocNames = new HashSet<>();
        for (int i=0; i < Math.min(10, topDocs.scoreDocs.length); i++) {
            retDocNames.add(reader.document(topDocs.scoreDocs[i].doc).get(TrecDocIndexer.FIELD_ID));
        }

        float proConRatioRel = proConRatio(relDocNames);
        float proConRatioRet = proConRatio(retDocNames);
        return proConRatioRet*proConRatioRel;
    }

    private float retrieveFairViaKMeansClustering(BufferedWriter bw, TRECQuery query) throws Exception {
        final float topTermsRatio = Float.parseFloat(getProperties().getProperty("topterms.ratio", "1.0"));
        // Get the candidate top documents
        TopDocs topDocs = searcher.search(query.getLuceneQueryObj(), numWanted);
        // Run a K-means
        KMeansReranker kMeansReranker = new KMeansReranker(reader, topDocs, 2, topTermsRatio);
        TopDocs rerankedDocs = kMeansReranker.rerank(numWanted);

        saveRetrievedTuples(bw, query, topDocs);
        return computeProConRatio(query.id, rerankedDocs);

        //System.out.println("Before rerank:");
        //System.out.println(ScoreDocUtils.toString(topDocs.scoreDocs));
        //System.out.println("After rerank");
        //System.out.println(ScoreDocUtils.toString(rerankedDocs.scoreDocs));
    }

    private void retrieve(BufferedWriter bw, TRECQuery query, AllRelRcds relRcds) throws Exception {
        String qid = query.id;
        HashMap<String, Float> relDocMap= relRcds.getRelInfo(qid).getRelDocs();
        Set<String> relDocs =
                relDocMap
                .keySet().stream()
                .collect(Collectors.toSet())
        ;

        for (String docName: relDocs) {
            Document relDoc = getRelDoc(docName);
            String docText = relDoc.get(TrecDocIndexer.FIELD_ANALYZED_CONTENT);
            TopDocs topDocs = retrieveConstrained(query.title, docText);
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

    float retrieveBaseline(BufferedWriter bw, TRECQuery query) throws Exception {
        TopDocs topDocs = retrieve(query);
        saveRetrievedTuples(bw, query, topDocs);
        return computeProConRatio(query.id, topDocs);
    }

    public void retrieveAll(List<TRECQuery> queries) throws Exception {
        TopDocs topDocs;
        boolean constrained = Boolean.parseBoolean(prop.getProperty("retrieval.constrained", "false"));
        String suffix = !constrained? "": ".constrained";
        String resFile = prop.getProperty("res.file") + suffix;

        BufferedWriter bw = new BufferedWriter(new FileWriter(resFile));
        System.out.println("Saving results to: " + resFile);

        float aggregateProConRatio = 0;
        for (TRECQuery query : queries) {
            System.out.print("Retrieving for query " + query.id + "\r");
            aggregateProConRatio += constrained? retrieveFairViaKMeansClustering(bw, query): retrieveBaseline(bw, query);
        }
        bw.close();
        System.out.println(String.format("Pro-Con ratio = %.4f", aggregateProConRatio/queries.size()));
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

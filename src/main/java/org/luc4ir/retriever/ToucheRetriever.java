package org.luc4ir.retriever;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.luc4ir.trec.TRECQuery;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class ToucheRetriever extends MsMarcoTopDocs {

    public ToucheRetriever(String propFile, Similarity sim) throws Exception {
        super(propFile, sim);
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

    public static void main(String[] args) {
        try {
            ToucheRetriever toucheRetriever =
                    new ToucheRetriever("touche.properties",
                        //new LMJelinekMercerSimilarity(0.2f)
                        new BM25Similarity()
                    );

            toucheRetriever.retrieveAll();
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
}

package org.luc4ir.retriever;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.store.FSDirectory;
import org.luc4ir.indexing.TrecDocIndexer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.Charset;
import java.util.List;

public class WikiPassageRetriever {
    public static void main(String[] args) {
        final String QUERY_FILE = "/Users/debasis/research/common/stereoset/sentences.txt";
        final String WIKI_INDEX = "/Users/debasis/research/common/wiki/index/";

        try {
            List<String> queries = FileUtils.readLines(new File(QUERY_FILE), Charset.defaultCharset());
            IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(WIKI_INDEX).toPath()));
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new LMDirichletSimilarity());

            StandardQueryParser queryParser = new StandardQueryParser(TrecDocIndexer.analyzer());
            StringBuffer buff = new StringBuffer();
            int qc = 1;
            BufferedWriter bw = new BufferedWriter(new FileWriter("top-50-wiki-stereoset.txt"));

            for (String query: queries) {
                Query luceneQuery = queryParser.parse(query, TrecDocIndexer.FIELD_ANALYZED_CONTENT);
                buff.append(qc).append("\t").append(query).append("\n");
                TopDocs topDocs = searcher.search(luceneQuery, 50);
                for (ScoreDoc sd: topDocs.scoreDocs) {
                    Document d = reader.document(sd.doc);
                    buff.append(d.get(TrecDocIndexer.FIELD_ANALYZED_CONTENT)).append("\n");
                }
                bw.write(buff.toString());
                buff.setLength(0);
                qc++;
            }

            reader.close();
            bw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

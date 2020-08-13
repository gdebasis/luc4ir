/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.indexing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.xml.sax.helpers.DefaultHandler;

/**
 *
 * @author Debasis
 */

public class TrecDocIndexer {
    Properties prop;
    String saxparser;
    
    File indexDir;
    IndexWriter writer;
    Analyzer analyzer;
    List<String> stopwords;
    int pass;
    
    static final public String FIELD_ID = "id";
    static final public String FIELD_ANALYZED_CONTENT = "words";  // Standard analyzer w/o stopwords.

    protected List<String> buildStopwordList(String stopwordFileName) {
        List<String> stopwords = new ArrayList<>();
        String stopFile = prop.getProperty(stopwordFileName);        
        String line;

        try (FileReader fr = new FileReader(stopFile);
            BufferedReader br = new BufferedReader(fr)) {
            while ( (line = br.readLine()) != null ) {
                stopwords.add(line.trim());
            }
            br.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return stopwords;
    }

    Analyzer constructAnalyzer() {
        Analyzer eanalyzer = new EnglishAnalyzer(
            StopFilter.makeStopSet(buildStopwordList("stopfile"))); // default analyzer
        return eanalyzer;        
    }
    
    public TrecDocIndexer(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));                
        analyzer = constructAnalyzer();            
        String indexPath = prop.getProperty("index");        
        indexDir = new File(indexPath);
        // generic or structured
        saxparser = prop.getProperty("sax.parser", "generic");        
    }
    
    public Analyzer getAnalyzer() { return analyzer; }
    
    public Properties getProperties() { return prop; }
    
    void processAll() throws Exception {
        System.out.println("Indexing TREC collection...");
        
        IndexWriterConfig iwcfg = new IndexWriterConfig(analyzer);
        iwcfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        writer = new IndexWriter(FSDirectory.open(indexDir.toPath()), iwcfg);
        
        indexAll();
        
        writer.close();
    }
    
    public File getIndexDir() { return indexDir; }
        
    void indexAll() throws Exception {
        if (writer == null) {
            System.err.println("Skipping indexing... Index already exists at " + indexDir.getName() + "!!");
            return;
        }
        
        File topDir = new File(prop.getProperty("coll"));
        indexDirectory(topDir);
    }

    private void indexDirectory(File dir) throws Exception {
        File[] files = dir.listFiles();
        for (int i=0; i < files.length; i++) {
            File f = files[i];
            if (f.isDirectory()) {
                System.out.println("Indexing directory " + f.getName());
                indexDirectory(f);  // recurse
            }
            else
                indexFile(f);
        }
    }
    
    Document constructDoc(String id, String content) throws IOException {
        Document doc = new Document();
        doc.add(new Field(FIELD_ID, id, Field.Store.YES, Field.Index.NOT_ANALYZED));

        // For the 1st pass, use a standard analyzer to write out
        // the words (also store the term vector)
        doc.add(new Field(FIELD_ANALYZED_CONTENT, content,
                Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.YES));
        
        return doc;
    }

    void indexFile(File file) throws Exception {
        System.out.println("Indexing file: " + file.getName());
        
        InputStream is = !file.getName().endsWith("gz")?
                        new FileInputStream(file):
                        new GZIPInputStream(new FileInputStream(file));
        
        if (saxparser!=null)
            indexFileWithSAX(is);
        else
            indexFileWithDOM(is);
        
        if (is!=null)
            is.close();
    }

    void indexFileWithSAX(InputStream is) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();        
        DefaultHandler handler;
        
        String docStartTag = prop.getProperty("sax.docstart");
        String docIdTag = prop.getProperty("sax.docid");
        String contentTags = prop.getProperty("sax.content_tags");
        handler = new GenericSAXParserHandler(writer, docStartTag, docIdTag, contentTags);
            
        saxParser.parse(is, handler);        
    }
    
    
    void indexFileWithDOM(InputStream is) throws Exception {
        Document doc;

        org.jsoup.nodes.Document jdoc = Jsoup.parse(is, "UTF-8", "");
        Elements docElts = jdoc.select("DOC");

        for (Element docElt : docElts) {
            Element docIdElt = docElt.select("DOCNO").first();
            doc = constructDoc(docIdElt.text(), docElt.text());
            writer.addDocument(doc);
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java TrecDocIndexer <prop-file>");
            args[0] = "init.properties";
        }

        try {
            TrecDocIndexer indexer = new TrecDocIndexer(args[0]);
            indexer.processAll();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }    
}

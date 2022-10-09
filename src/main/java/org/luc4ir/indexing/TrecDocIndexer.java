/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.indexing;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
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
    String parser;
    
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

    static public Analyzer analyzer() throws IOException {
        List<String> stopwords = FileUtils.readLines(new File("stop.txt"), Charset.defaultCharset());
        Analyzer eanalyzer = new EnglishAnalyzer(StopFilter.makeStopSet(stopwords)); // default analyzer
        return eanalyzer;
    }
    
    public TrecDocIndexer(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));                
        analyzer = constructAnalyzer();            
        String indexPath = prop.getProperty("index");        
        indexDir = new File(indexPath);
        // generic or structured
        parser = prop.getProperty("parser");        
    }
    
    public Analyzer getAnalyzer() { return analyzer; }
    
    public Properties getProperties() { return prop; }
 
    public void indexTarGz() {
        String tarGzippedFile = prop.getProperty("coll");
        
        try {
            String line;
            StringBuffer buff = new StringBuffer();
            TarArchiveInputStream tarInput = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(tarGzippedFile)));
            TarArchiveEntry currentEntry = tarInput.getNextTarEntry();
            
            while (currentEntry != null) {
                InputStreamReader isr = new InputStreamReader(tarInput);
                BufferedReader br = new BufferedReader(isr); // Read directly from tarInput
                String tarEntryName =  currentEntry.getName();
                String fileName = new File(tarEntryName).getName();
                
                if (fileName.charAt(0) != '.') {                
                    System.out.println("Indexing file: " + tarEntryName);
                    while ((line = br.readLine()) != null) {
                        buff.append(line).append("\n");
                    }
                    indexFileWithDOM(buff.toString());
                }
                
                currentEntry = tarInput.getNextTarEntry(); // iterate to the next file
                buff.setLength(0); // clear buffer
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    void testIndex() {
        // Test if the index can be opened. Print first two docs
        try {
            IndexReader reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
            Document d = reader.document(0);
            System.out.println(
                d.get(TrecDocIndexer.FIELD_ID) + "\t" +
                d.get(TrecDocIndexer.FIELD_ANALYZED_CONTENT)
            );
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    void processAll() throws Exception {
        System.out.println("Indexing collection...");
        
        IndexWriterConfig iwcfg = new IndexWriterConfig(analyzer);
        iwcfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        writer = new IndexWriter(FSDirectory.open(indexDir.toPath()), iwcfg);

        String collType = prop.getProperty("colltype", "fsdir");  // fsdir/targz
        
        if (collType.equals("fsdir"))
            indexAll();
        else
            indexTarGz();

        writer.close();
        //testIndex();
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
        System.out.println("Indexing files in directory " + dir.getAbsolutePath());
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

    static Field constructIDField(String id) {
        FieldType fieldType = new FieldType();
        fieldType.setIndexOptions(IndexOptions.DOCS);
        fieldType.setStored(true);    // default = false (same as Field.Store.NO)
        fieldType.setTokenized(false);  // default = true (tokenize the content)
        fieldType.setOmitNorms(false); // default = false (used when scoring)
        Field idField = new Field(FIELD_ID, id, fieldType);
        return idField;
    }

    static Field constructContentField(String content) {
        FieldType contentFieldType = new FieldType();
        contentFieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
        contentFieldType.setStored(true);    // default = false (same as Field.Store.NO)
        contentFieldType.setTokenized(true);  // default = true (tokenize the content)
        contentFieldType.setOmitNorms(false); // default = false (used when scoring)
        contentFieldType.setStoreTermVectors(true);
        Field contentField = new Field(FIELD_ANALYZED_CONTENT, content, contentFieldType);
        return contentField;
    }

    Document constructDoc(String id, String content) throws IOException {

        Field idField = constructIDField(id);
        Field contentField = constructContentField(content);

        Document doc = new Document();
        doc.add(idField);
        doc.add(contentField);

        /* Lucene 5 code
        doc.add(new Field(FIELD_ID, id, Field.Store.YES, Field.Index.NOT_ANALYZED));

        // For the 1st pass, use a standard analyzer to write out
        // the words (also store the term vector)
        doc.add(new Field(FIELD_ANALYZED_CONTENT, content,
                Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.YES));
        */

        return doc;
    }

    void indexFile(File file) throws Exception {
        System.out.println("Indexing file: " + file.getName());
        
        InputStream is = !file.getName().endsWith("gz")?
                        new FileInputStream(file):
                        new GZIPInputStream(new FileInputStream(file));
        
        if (parser.equals("generic"))
            indexFileWithSAX(is);
        else if (parser.equals("annotated_lines"))
            indexFileWithLineReader(is);
        else if (parser.equals("line_simple"))
            indexFindexFileWithLineReaderSimple(is);
        else // put 'dom'... any other string (e.g. 'none') also works!
            indexFileWithDOM(is);
        
        if (is!=null)
            is.close();
    }

    // MSMARCO format... docid, text
    void indexFindexFileWithLineReaderSimple(InputStream is) throws Exception {
        Document doc;
        String id = null;
        String line;
        int docCount = -1;

        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));

        while ((line = br.readLine())!= null) {
            line = line.trim();
            String[] parts = line.split("\t");
            if (parts.length >= 2) {
                doc = constructDoc(parts[0], parts[1]);
                writer.addDocument(doc);

                if (docCount++ % 10000 == 0)
                    System.out.print(String.format("Indexed %d passages from MSMARCO\r", docCount));
            }
        }
        System.out.println();
    }

    void indexFileWithLineReader(InputStream is) throws Exception {
        Document doc;
        String id = null;
        StringBuffer contentBuff = new StringBuffer();
        String line;
        boolean startAccumulating = false;
        int docCount = 0;
        
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        
        while ((line = br.readLine())!= null) {
            line = line.trim();
            
            if (line.startsWith("<pno>")) {
                id = line.split("\\s+")[1];
                startAccumulating = true;
                docCount++;
            }
            else if (line.equals("</p>")) {
                doc = constructDoc(id, contentBuff.toString());
                writer.addDocument(doc);
                contentBuff.setLength(0); // clear buffer
                startAccumulating = false;
                
                if (docCount % 10000 == 0)
                    System.out.println(String.format("Indexed %d passages from Wiki", docCount));
            }
            else if (startAccumulating) {
                line = removeTags(line);
                contentBuff.append(line).append("\n");
            }
        }
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
    
    void indexFileWithDOM(String text) throws Exception {
        Document doc;

        InputStream is = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        org.jsoup.nodes.Document jdoc = Jsoup.parse(is, "UTF-8", "");
        Elements docElts = jdoc.select("DOC");

        for (Element docElt : docElts) {
            Element docIdElt = docElt.select("DOCNO").first();
            doc = constructDoc(docIdElt.text(), docElt.text());
            writer.addDocument(doc);
        }
    }
    
    public static String analyze(Analyzer analyzer, String query) {

        StringBuffer buff = new StringBuffer();
        try {
            TokenStream stream = analyzer.tokenStream("dummy", new StringReader(query));
            CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                String term = termAtt.toString();
                buff.append(term).append(" ");
            }
            stream.end();
            stream.close();

            if (buff.length()>0)
                buff.deleteCharAt(buff.length()-1);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }

        return buff.toString();
    }
    
    static String removeTags(String txt) {
        return Jsoup.parse(txt).text();
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java TrecDocIndexer <prop-file>");
            return;
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

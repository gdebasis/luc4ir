/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.indexing;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 *
 * @author debforit
 */

class BasicDocUnit {
    String id;
    String content;
    
    static final public String FIELD_ID = "id";
    static final public String FIELD_CONTENT = "words";    

    public BasicDocUnit() {
        id = null;
        content = "";
    }
    
    @Override
    public String toString() {
        StringBuffer buff = new StringBuffer();
        buff
            .append("id: ")
            .append(id)
            .append("\n")
            .append("content: ")
            .append(content)
        ;
        return buff.toString();
    }
    
    Document constructDocument() {
        Document doc = new Document();

        Field idField = TrecDocIndexer.constructIDField(id);
        Field contentField = TrecDocIndexer.constructContentField(content);

        doc.add(idField);
        doc.add(contentField);

        /* Lucene 5.1 code
        doc.add(new Field(FIELD_ID, id, Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(FIELD_CONTENT, content, Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.YES));
         */
        return doc;
    }
    
}

public class GenericSAXParserHandler extends DefaultHandler {
    
    IndexWriter writer;    
    StringBuffer buff;
    String currentTag;
    
    // Mandatory tags
    String docStartTag;
    String idTag;
    // Content tags
    String[] tagNamesToAccumulate;
    
    BasicDocUnit doc;

    public GenericSAXParserHandler(IndexWriter writer, String docStartTag, String idTag, String contentTags) {
        this.writer = writer;
        this.docStartTag = docStartTag;
        this.idTag = idTag;
        
        buff = new StringBuffer();
        tagNamesToAccumulate = contentTags.split(",");
        
        Arrays.sort(tagNamesToAccumulate);
    }
    
    boolean isTagOfInterest(String qName) {
        if (qName.equals(idTag))
            return true;
        
        int index = Arrays.binarySearch(tagNamesToAccumulate, qName);
        if (index >= 0 && index < tagNamesToAccumulate.length) {
            if (tagNamesToAccumulate[index].equals(qName))
                return true;
        }
        return false;
    }
    
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (currentTag!=null)
            buff.append(new String(ch, start, length)).append(" ");
    }
    
    @Override
    public void startElement(String uri, String lName, String qName, Attributes attr) throws SAXException {    
        if (qName.equals(docStartTag)) {
            doc = new BasicDocUnit();
        }
        if (isTagOfInterest(qName))        
            currentTag = qName;
    }
    
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (qName.equals(docStartTag)) {
            try {
                writer.addDocument(doc.constructDocument());
                //System.err.println(doc);
            }
            catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        
        if (!isTagOfInterest(qName))
            return;
            
        if (qName.equals(idTag)) {
            if (doc.id==null)
                doc.id = buff.toString();
        }
        doc.content = buff.toString();
        
        buff.setLength(0); // we're done accumulating... clear the buffer
        currentTag = null;
    }
    
    public static void main(String[] args) {
        String sampleDoc = "samplecoll/NCT00889720.xml";
        
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();        
            GenericSAXParserHandler handler = new GenericSAXParserHandler(null,
                        "clinical_study", "nct_id",
                    "brief_summary,detailed_description,study_design_info,intervention,eligibility,clinical_results");
            saxParser.parse(new File(sampleDoc), handler);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}

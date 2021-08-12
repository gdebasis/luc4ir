/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.evaluator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

/**
 *
 * @author Debasis
 */

public class Evaluator {
    AllRelRcds relRcds;
    AllRetrievedResults retRcds;
    static boolean graded;
    static int threshold;

    public Evaluator(String qrelsFile, String resFile) {
        relRcds = new AllRelRcds(qrelsFile);
        retRcds = new AllRetrievedResults(resFile);
        graded = true;
        threshold = 1;
    }
    
    public Evaluator(Properties prop) {
        String qrelsFile = prop.getProperty("qrels.file");
        String resFile = prop.getProperty("res.file");
        graded = Boolean.parseBoolean(prop.getProperty("evaluate.graded", "false"));
        if (graded)
            threshold = Integer.parseInt(prop.getProperty("evaluate.graded_to_bin.threshold", "1"));
        else
            threshold = 1;
        relRcds = new AllRelRcds(qrelsFile);
        retRcds = new AllRetrievedResults(resFile);        
    }
    
    public void load() throws Exception {
        relRcds.load();
        retRcds.load();
    }
    
    public void fillRelInfo() {
        retRcds.fillRelInfo(relRcds);
    }
    
    public String computeAll() {
        return retRcds.computeAll();
    }
    
    @Override
    public String toString() {
        StringBuffer buff = new StringBuffer();
        buff.append(relRcds.toString()).append("\n");
        buff.append(retRcds.toString());
        return buff.toString();
    }

    public AllRelRcds getRelRcds() {
        return relRcds;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            args = new String[1];
            args[0] = "init.properties";
        }
        try {
            Properties prop = new Properties();
            prop.load(new FileReader(args[0]));
            
            String qrelsFile = prop.getProperty("qrels.file");
            String resFile = prop.getProperty("res.file");
            
            Evaluator evaluator = new Evaluator(qrelsFile, resFile);
            evaluator.load();
            evaluator.fillRelInfo();
            System.out.println(evaluator.computeAll());
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        
    }
    
}

package org.luc4ir.orcas;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.luc4ir.indexing.TrecDocIndexer;

import java.io.*;
import java.util.*;

public class OrcasQueryPairPreparator {
    Map<String, String> query2Qid;
    Set<String> qrySubset;

    Analyzer analyzer = new StandardAnalyzer();
    static final String OUTPUT_FILE = "orcas/labeled_data.txt";

    String preprocess(String query) {
        return TrecDocIndexer.analyze(analyzer, query);
    }

    OrcasQueryPairPreparator(String qidQryFile, String qrySubsetFile) {
        loadQueryQidMap(qidQryFile);
        System.out.println("#qid-query-map = " + this.query2Qid.size());

        loadQrySubset(qrySubsetFile);
        System.out.println("#queries in subset = " + this.qrySubset.size());
    }

    void loadQueryQidMap(String qidQryFile) {
        query2Qid = new HashMap<>();
        String line;
        try (BufferedReader br = new BufferedReader(new FileReader(qidQryFile))) {
            while ((line = br.readLine())!= null) {
                String[] parts = line.split("\t");
                if (parts.length < 2) continue;
                String pp = preprocess(parts[1]);
                query2Qid.put(pp, parts[0].trim());
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    void loadQrySubset(String querySubsetFile) {
        qrySubset = new HashSet<>();
        String line;
        try (BufferedReader br = new BufferedReader(new FileReader(querySubsetFile))) {
            while ((line = br.readLine())!= null) {
                String pp = preprocess(line);
                this.qrySubset.add(pp);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    void makeSubset(String labeledDataFile) {
        String line;
        String qry1, qry2, qid1, qid2;

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(OUTPUT_FILE));
            BufferedReader br = new BufferedReader(new FileReader(labeledDataFile))) {
            while ((line = br.readLine())!= null) {
                String[] parts = line.split("\t");
                if (parts.length < 3) continue;

                qry1 = preprocess(parts[0]);
                qry2 = preprocess(parts[1]);

                if (!(qrySubset.contains(qry1) && qrySubset.contains(qry2)))
                    continue;

                qid1 = query2Qid.get(qry1);
                qid2 = query2Qid.get(qry2);

                if (qid1==null || qid2==null) continue;
                if (qid1.equals(qid2)) continue;

                bw.write(String.format("%s\t%s\t%s\t%s\t%s", qid1, qry1, qid2, qry2, parts[2]));
                bw.newLine();
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Output a labeled datafile comprised of qid1-query1-qid2-query2-label
        // selected from a subset specified by orcas/query_subset.txt.
        final String ORCAS_QID_QUERY_FILE = "orcas/qid_query.txt";
        final String ORCAS_QRY_SUBSET = "orcas/query_subset.txt";
        final String ORCAS_LABELED_QRY_PAIR_DATA = "orcas/labeled_query_pairs.tsv";

        OrcasQueryPairPreparator orcasQueryPairPreparator =
                new OrcasQueryPairPreparator(
                        ORCAS_QID_QUERY_FILE,
                        ORCAS_QRY_SUBSET
                );
        orcasQueryPairPreparator.makeSubset(ORCAS_LABELED_QRY_PAIR_DATA);
    }
}

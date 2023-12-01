package org.luc4ir.feedback;

import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;
import org.apache.pdfbox.preflight.metadata.PDFAIdentificationValidation;
import org.luc4ir.evaluator.DocVector;
import org.luc4ir.indexing.TrecDocIndexer;
import org.luc4ir.retriever.TrecDocRetriever;
import org.luc4ir.trec.TRECQuery;

import java.util.*;

import java.io.IOException;
import java.util.stream.Collectors;

public class DiversityReranker extends KLDivReranker {
    int k; // number of diverse documents reported at top k
    int m; // number of candidate docs for reranking
    TrecDocRetriever retriever;

    public DiversityReranker(TrecDocRetriever retriever, TRECQuery query, TopDocs topDocs, int k) {
        super(topDocs);
        this.retriever = retriever;
        try {
            RelevanceModelIId fdbkModel = new RelevanceModelConditional(retriever, query, topDocs);
            try {
                fdbkModel.computeFdbkWeights();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            this.setStats(topDocs, fdbkModel.retrievedDocsTermStats);
            m = fdbkModel.numTopDocs;
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

        this.k = k; // subset of documents required. topdocs.length MUST be > k
    }

    void swap(TopDocs topDocs, int i, int j) {
        if (i==j) return;
        ScoreDoc tmp = new ScoreDoc(topDocs.scoreDocs[j].doc, topDocs.scoreDocs[j].score);
        topDocs.scoreDocs[j] = topDocs.scoreDocs[i];
        topDocs.scoreDocs[i] = tmp;
    }

    void updateLM(HashMap<String, RetrievedDocTermInfo> lm, int selectedDoc) {
        PerDocTermVector pdv = this.retrievedDocsTermStats.docTermVectorMap.get(selectedDoc);
        HashMap<String, RetrievedDocTermInfo> del = pdv.getPerDocStats();
        for (Map.Entry<String, RetrievedDocTermInfo> docTermInfoEntry: del.entrySet()) {
            String term = docTermInfoEntry.getKey();
            RetrievedDocTermInfo w = lm.get(term);
            if (w==null) {
                w = new RetrievedDocTermInfo(term, 1);
                lm.put(term, w);
            }
            w.wt += docTermInfoEntry.getValue().wt;
        }
    }

    int selectNextDoc(TopDocs topDocs, HashMap<String, RetrievedDocTermInfo> pool, int start, int N) {
        int docToSelectNext = 0; float max_score = Float.MIN_VALUE;
        float[] div_scores = new float[N];
        float sim_with_selected;
        float p_w_D;    // P(w|D) for this doc D

        // For each document
        for (int i=start; i < N; i++) {
            ScoreDoc sd = topDocs.scoreDocs[i];
            PerDocTermVector docVector = this.retrievedDocsTermStats.docTermVectorMap.get(sd.doc); // current doc
            sim_with_selected = 0; // for this doc

            // For each v \in V (vocab of top ranked documents) -- compute score for this doc
            for (RetrievedDocTermInfo w: pool.values()) {
                try {
                    w.wt = w.tf/(float)pool.size() * (float) Math.log(retriever.getReader().numDocs() / (float)
                            retriever.getReader().docFreq(
                                    new Term(TrecDocIndexer.FIELD_ANALYZED_CONTENT, w.getTerm()))
                    );
                } catch (Exception ex) { ex.printStackTrace(); }

                p_w_D = docVector.getNormalizedTf(w.getTerm());
                //System.out.println(String.format("term:#%s# wt(d) = %.4f, w(M) = %.4f", w.term, p_w_D, w.wt));
                sim_with_selected += w.wt * p_w_D; // wt already has idf
            }
            div_scores[i] = sd.score/sim_with_selected; // max numerator, min denom
        }

        for (int i=start; i < N; i++) {
            ScoreDoc sd = topDocs.scoreDocs[i];
            if (div_scores[i] > max_score) {
                max_score = div_scores[i];
                docToSelectNext = i;
            }
            System.out.println(String.format("div_score[%d] = %.4f", i, div_scores[i]));
        }

        return docToSelectNext;
    }

    @Override
    public TopDocs rerankDocs() {
        HashMap<String, RetrievedDocTermInfo> pool =
                this.retrievedDocsTermStats.docTermVecs.get(0).perDocStats;
        List<ScoreDoc> reranked = new ArrayList(this.topDocs.scoreDocs.length);
        reranked.add(topDocs.scoreDocs[0]);

        int start = 1;
        int selectedDocIndex;
        int numSelected = 0;

        while (numSelected < k) {
            if (start >= m)
                break;

            selectedDocIndex = selectNextDoc(topDocs, pool, start, m);
            reranked.add(topDocs.scoreDocs[selectedDocIndex]);
            updateLM(pool, topDocs.scoreDocs[selectedDocIndex].doc); // expand the LM of what's selected

            numSelected++;

            swap(topDocs, start, selectedDocIndex); // push the selected doc at the top
            start++; // repeat o the remaining set
        }

        // Add the remainder to the list
        for (int i=start; i< topDocs.scoreDocs.length; i++)
            reranked.add(topDocs.scoreDocs[i]);

        TopDocs rerankedDocs = new TopDocs(topDocs.totalHits, reranked.stream().toArray(ScoreDoc[]::new));
        int rank=1;
        for (ScoreDoc scoreDoc: rerankedDocs.scoreDocs)
            scoreDoc.score = 1.0f/rank++; // we make the scores a monotonically decreasing sequence

        return rerankedDocs;
    }
}

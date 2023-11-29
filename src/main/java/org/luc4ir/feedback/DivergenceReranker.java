package org.luc4ir.feedback;

import jdk.internal.jshell.tool.StopDetectingInputStream;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;
import org.apache.pdfbox.preflight.metadata.PDFAIdentificationValidation;
import org.luc4ir.evaluator.DocVector;

import java.util.*;

import java.io.IOException;
import java.util.stream.Collectors;

public class DivergenceReranker extends KLDivReranker {
    int k;
    public DivergenceReranker(TopDocs topDocs, RetrievedDocsTermStats retrievedDocsTermStats, int k) {
        super(topDocs, retrievedDocsTermStats);
        this.k = k; // subset of documents required. topdocs.length MUST be > k
    }

    void swap(TopDocs topDocs, int i, int j) {
        ScoreDoc tmp = new ScoreDoc(topDocs.scoreDocs[j].doc, topDocs.scoreDocs[j].score);
        topDocs.scoreDocs[j] = topDocs.scoreDocs[i];
        topDocs.scoreDocs[i] = tmp;
    }

    void updateLM(HashMap<String, RetrievedDocTermInfo> lm, int selectedDoc) {
        PerDocTermVector pdv = this.retrievedDocsTermStats.docTermVectorMap.get(selectedDoc);
        HashMap<String, RetrievedDocTermInfo> del = pdv.getPerDocStats();
        for (Map.Entry<String, RetrievedDocTermInfo> docTermInfoEntry: del.entrySet()) {
            String termId = docTermInfoEntry.getKey();
            RetrievedDocTermInfo w = lm.get(termId);
            if (w==null) {
                lm.put(termId, w);
            }
            w.wt += docTermInfoEntry.getValue().wt;
        }
    }

    int selectNextDoc(TopDocs topDocs, HashMap<String, RetrievedDocTermInfo> selected, int start, int N) {
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
            for (RetrievedDocTermInfo w: selected.values()) {
                p_w_D = docVector.getNormalizedTf(w.getTerm());
                sim_with_selected += w.wt * p_w_D; // wt already has idf
            }

            div_scores[i] = sd.score/sim_with_selected; // max numerator, min denom
        }

        for (int i=start; i < topDocs.scoreDocs.length; i++) {
            ScoreDoc sd = topDocs.scoreDocs[i];
            if (div_scores[i] > max_score) {
                max_score = div_scores[i];
                docToSelectNext = i;
            }
        }

        return docToSelectNext;
    }

    @Override
    public TopDocs rerankDocs() {
        HashMap<String, RetrievedDocTermInfo> selected =
                this.retrievedDocsTermStats.docTermVecs.get(0).perDocStats;
        List<ScoreDoc> reranked = new ArrayList(this.topDocs.scoreDocs.length);
        reranked.add(topDocs.scoreDocs[0]);

        int start = 1, N = topDocs.scoreDocs.length;
        int selectedDocIndex;
        int numSelected = 0;

        while (numSelected < k) {
            if (start >= N)
                break;

            selectedDocIndex = selectNextDoc(topDocs, selected, start, N);
            reranked.add(topDocs.scoreDocs[selectedDocIndex]);
            updateLM(selected, topDocs.scoreDocs[selectedDocIndex].doc); // expand the LM of what's selected
            numSelected++;

            swap(topDocs, topDocs.scoreDocs.length-1, selectedDocIndex);
            start++; N--;
        }

        TopDocs rerankedDocs = new TopDocs(topDocs.totalHits, reranked.stream().toArray(ScoreDoc[]::new));
        return rerankedDocs;
    }
}

package org.luc4ir.retriever;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHits;
import org.luc4ir.evaluator.DocVector;
import org.luc4ir.genutils.ScoreDocUtils;
import org.luc4ir.indexing.TrecDocIndexer;

public class KMeansReranker {
    DocVector[] docVecs;
    int[] clusterIds;
    int K;
    int N;
    int MAXITER = 5;
    TopDocs topDocs;
    Random generator = new Random(42);

    public KMeansReranker(IndexReader reader, TopDocs topDocs, int K, float alpha) throws IOException {
        this.K = K;
        this.topDocs = topDocs;

        N = topDocs.scoreDocs.length;
        docVecs = new DocVector[N];
        clusterIds = new int[N];
        int i = 0;

        for (ScoreDoc sd: topDocs.scoreDocs) {
            String text = reader.document(sd.doc).get(TrecDocIndexer.FIELD_ANALYZED_CONTENT);
            docVecs[i] = new DocVector(reader, text, alpha);
            clusterIds[i] = (int)(generator.nextDouble()*K);
            i++;
        }
    }

    List<DocVector> computeCentroids() {
        List<DocVector> centroids = new ArrayList<>();
        for (int i=0; i < K; i++) {
            centroids.add(computeCentroid(i));
        }
        return centroids;
    }

    DocVector computeCentroid(int k) { // centroid for the kth component
        List<DocVector> vecs = new ArrayList<>();
        for (int i=0; i < docVecs.length; i++) {
            if (clusterIds[i] == k)
                vecs.add(docVecs[i]);
        }

        /*
        System.out.println("Documents in cluster:");
        int i = 0;
        for (DocVector dvec: vecs) {
            System.out.println(i + ": " + dvec.toString());
            i++;
        }
        */

        DocVector centroid = DocVector.computeCentroid(vecs);
        //System.out.println("Centroid:");
        //System.out.println(centroid);
        return centroid;
    }

    int nearestCentroid(DocVector d, List<DocVector> centroids) {
        int i=0, nn_id = 0;
        float maxSim = 0, sim;
        for (DocVector cvec: centroids) {
            sim = d.cosineSim(cvec);
            //System.out.println(String.format("sim(%d) = %.4f", i, sim));
            if (sim > maxSim) {
                maxSim = sim;
                nn_id = i;
            }
            i++;
        }
        return nn_id;
    }


    float assignClusterIds(List<DocVector> centroids) {
        int numChanged = 0;
        for (int i=0; i < N; i++) {
            DocVector dvec = docVecs[i];
            int clusterToAssign = nearestCentroid(dvec, centroids); // assign each document to its nearest centroid
            if (clusterIds[i] != clusterToAssign) // change of cluster
                numChanged++;
            clusterIds[i] = clusterToAssign;
        }
        return numChanged/(float)N;
    }

    void showClusterIds() {
        for (int k=0; k<K; k++) {
            System.out.println("Documents in cluster: " + k);
            for (int i=0; i<N; i++) {
                if (k==clusterIds[i]) {
                    System.out.print(i + ", ");
                }
            }
            System.out.println();
        }
    }

    private void cluster() { // first k docs as k centroids
        for (int i=1; i<=MAXITER; i++) {
            //System.out.println("Iteration: " + i);
            //showClusterIds();
            List<DocVector> centroids = computeCentroids();
            float changeRatio = assignClusterIds(centroids);
            if (changeRatio < 0.05) {
                //System.out.println(String.format("K-means converged after %d iterations", i));
                break;
            }
        }
    }

    List<ScoreDoc> topDocsForACluster(int k) {
        List<ScoreDoc> scoreDocs = new ArrayList<>();
        int i=0;
        for (ScoreDoc sd: this.topDocs.scoreDocs) {
            if (clusterIds[i] == k) {
                scoreDocs.add(sd);
            }
            i++;
        }
        return scoreDocs;
    }

    TopDocs rerank(int numWanted) {
        cluster();
        List<ScoreDoc> reranked = new ArrayList<>();
        List<ScoreDoc>[] topDocsForACluster = new ArrayList[K];
        for (int i=0; i<K; i++) {
            topDocsForACluster[i] = topDocsForACluster(i);
            //System.out.println(ScoreDocUtils.toString(topDocsForACluster[i]));
        }

        boolean added = false;
        while (reranked.size() < numWanted) {
            added = false;
            for (List<ScoreDoc> scoreDocList: topDocsForACluster) {
                if (!scoreDocList.isEmpty()) {
                    reranked.add(scoreDocList.remove(0));
                    added = true;
                }
            }
            if (!added)
                break;
        }

        numWanted = Math.min(numWanted, reranked.size());
        for (int rank=1; rank<=numWanted; rank++) {
            reranked.get(rank-1).score = 1/(float)rank;
        }

        return new TopDocs(new TotalHits(numWanted, TotalHits.Relation.EQUAL_TO),
                reranked.stream().toArray(ScoreDoc[]::new));
    }
}

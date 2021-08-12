package org.luc4ir.evaluator;

public class ResultTuple implements Comparable<ResultTuple> {
    String docName; // doc name
    int rank;       // rank of retrieved document
    float rel;    // is this relevant? comes from qrel-info

    public ResultTuple(String docName, int rank) {
        this.docName = docName;
        this.rank = rank;
    }

    public ResultTuple(String docName, int rank, float rel) {
        this.docName = docName;
        this.rank = rank;
        this.rel = rel;
    }

    @Override
    public int compareTo(ResultTuple t) {
        return rank < t.rank? -1 : rank == t.rank? 0 : 1;
    }
}



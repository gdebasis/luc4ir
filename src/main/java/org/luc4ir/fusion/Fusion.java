package org.luc4ir.fusion;

import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

public interface Fusion {
    TopDocs combine(ScoreDoc[] a, ScoreDoc[] b, int k);
}

package org.luc4ir.genutils;
import java.util.Comparator;
import org.apache.lucene.search.ScoreDoc;

public class ScoreDocComparator implements Comparator<ScoreDoc> {

    @Override
    public int compare(ScoreDoc a, ScoreDoc b) {
        return Float.compare(b.score, a.score); // descending
    }
}

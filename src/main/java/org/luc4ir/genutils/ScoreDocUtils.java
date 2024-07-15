package org.luc4ir.genutils;

import org.apache.lucene.search.ScoreDoc;
import java.util.List;

public class ScoreDocUtils {
    static public String toString(ScoreDoc[] scoreDocs) {
        StringBuilder sb = new StringBuilder("<");
        for (ScoreDoc sd: scoreDocs) {
            sb.append("(").append(sd.doc).append(",").append(sd.score).append(")").append(" ");
        }
        sb.append(">");
        return sb.toString();
    }

    static public String toString(List<ScoreDoc> scoreDocs) {
        StringBuilder sb = new StringBuilder("<");
        for (ScoreDoc sd: scoreDocs) {
            sb.append("(").append(sd.doc).append(",").append(sd.score).append(")").append(" ");
        }
        sb.append(">");
        return sb.toString();
    }

}

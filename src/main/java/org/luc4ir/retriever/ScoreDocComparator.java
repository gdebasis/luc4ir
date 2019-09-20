/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.retriever;

import java.util.Comparator;
import org.apache.lucene.search.ScoreDoc;

/**
 * Sort by rsv
 * @author Debasis
 */
public class ScoreDocComparator implements Comparator<ScoreDoc> {

    @Override
    public int compare(ScoreDoc thisObj, ScoreDoc thatObj) {
        return Float.compare(thatObj.score, thisObj.score);  // descending
    }
}

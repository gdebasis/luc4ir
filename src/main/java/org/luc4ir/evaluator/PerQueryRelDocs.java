package org.luc4ir.evaluator;

import java.util.HashMap;

public class PerQueryRelDocs {
    String qid;
    HashMap<String, Float> relMap; // keyed by docid, entry stores the rel value (>0)
    int numRel;

    public PerQueryRelDocs(String qid) {
        this.qid = qid;
        numRel = 0;
        relMap = new HashMap<>();
    }

    public void addTuple(String docId, float rel) {
        if (relMap.get(docId) != null)
            return;
        if (rel > 0) {
            numRel++;
            relMap.put(docId, rel);
        }
    }

    public int isRel(String docName) {
        Float rel = relMap.get(docName);
        return rel==null? 0 : rel.intValue();
    }
}


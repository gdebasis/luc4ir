package org.luc4ir.feedback;

import org.apache.lucene.search.TopDocs;

public class HyperbolicVecReranker extends KLDivReranker {

    @Override
    public TopDocs rerankDocs() {
        return null;
    }
}

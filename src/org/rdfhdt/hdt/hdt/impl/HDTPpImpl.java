package org.rdfhdt.hdt.hdt.impl;

import org.rdfhdt.hdt.iterator.StarDictionaryTranslateIterator;
import org.rdfhdt.hdt.options.HDTOptions;
import org.rdfhdt.hdt.stars.IteratorStarString;
import org.rdfhdt.hdt.stars.StarID;
import org.rdfhdt.hdt.stars.StarString;
import org.rdfhdt.hdt.triples.impl.BitmapTriples;
import org.rdfhdt.hdt.triples.impl.BitmapTriplesPp;

public class HDTPpImpl extends HDTImpl {
    public HDTPpImpl(HDTOptions spec) {
        super(spec);
    }

    public void makeHDTpp() {
        triples = new BitmapTriplesPp((BitmapTriples)triples, dictionary);
    }

    public IteratorStarString search(StarString star){
        if(isClosed) {
            throw new IllegalStateException("Cannot search an already closed HDT++");
        }
        StarID s = star.toStarID(dictionary);

        return new StarDictionaryTranslateIterator(((BitmapTriplesPp)triples).search(s), dictionary, star);
    }
}

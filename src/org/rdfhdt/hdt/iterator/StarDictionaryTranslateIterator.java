package org.rdfhdt.hdt.iterator;

import org.rdfhdt.hdt.dictionary.Dictionary;
import org.rdfhdt.hdt.enums.ResultEstimationType;
import org.rdfhdt.hdt.enums.TripleComponentRole;
import org.rdfhdt.hdt.stars.IteratorStarID;
import org.rdfhdt.hdt.stars.IteratorStarString;
import org.rdfhdt.hdt.stars.StarID;
import org.rdfhdt.hdt.stars.StarString;
import org.rdfhdt.hdt.triples.TripleID;
import org.rdfhdt.hdt.util.Tuple;

import java.util.ArrayList;
import java.util.List;

public class StarDictionaryTranslateIterator implements IteratorStarString {
    /** The iterator of TripleID */
    final IteratorStarID iterator;
    /** The dictionary */
    final Dictionary dictionary;

    StarString star;

    /**
     * Basic constructor
     *
     * @param iteratorStarID
     *            Iterator of TripleID to be used
     * @param dictionary
     *            The dictionary to be used
     */
    public StarDictionaryTranslateIterator(IteratorStarID iteratorStarID, Dictionary dictionary) {
        this.iterator = iteratorStarID;
        this.dictionary = dictionary;
        this.star = new StarString();
    }

    /**
     * Basic constructor
     *
     * @param iteratorStarID
     *            Iterator of TripleID to be used
     * @param dictionary
     *            The dictionary to be used
     */
    public StarDictionaryTranslateIterator(IteratorStarID iteratorStarID, Dictionary dictionary, StarString star) {
        this.iterator = iteratorStarID;
        this.dictionary = dictionary;
        this.star = star;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.util.Iterator#hasNext()
     */
    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    /*
     * (non-Javadoc)
     *
     * @see java.util.Iterator#next()
     */
    @Override
    public StarString next() {
        StarID star = iterator.next();

        CharSequence subj = dictionary.idToString(star.getSubject(), TripleComponentRole.SUBJECT);

        List<Tuple<CharSequence, CharSequence>> lst = new ArrayList<>();
        int size = star.size();
        for(int i = 0; i < size; i++) {
            TripleID tpl = star.getTriple(i);
            Tuple<CharSequence, CharSequence> t = new Tuple<>(
                    dictionary.idToString(tpl.getPredicate(), TripleComponentRole.PREDICATE),
                    dictionary.idToString(tpl.getObject(), TripleComponentRole.OBJECT)
            );
            lst.add(t);
        }

        return new StarString(subj, lst);
//		return DictionaryUtil.tripleIDtoTripleString(dictionary, triple);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.util.Iterator#remove()
     */
    @Override
    public void remove() {
        iterator.remove();
    }

    /* (non-Javadoc)
     * @see hdt.iterator.IteratorTripleString#goToStart()
     */
    @Override
    public void goToStart() {
        iterator.goToStart();
    }

    @Override
    public long estimatedNumResults() {
        return iterator.estimatedNumResults();
    }

    @Override
    public ResultEstimationType numResultEstimation() {
        return iterator.numResultEstimation();
    }
}

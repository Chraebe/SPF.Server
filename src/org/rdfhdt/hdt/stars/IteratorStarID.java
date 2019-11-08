package org.rdfhdt.hdt.stars;

import org.rdfhdt.hdt.enums.ResultEstimationType;
import org.rdfhdt.hdt.enums.TripleComponentOrder;

import java.util.Iterator;

public interface IteratorStarID extends Iterator<StarID> {
    /**
     * Whether the iterator has previous elements.
     * @return
     */
    boolean hasPrevious();

    /**
     * Get the previous element. Call only if hasPrevious() returns true.
     * It moves the cursor of the Iterator to the previous entry.
     * @return
     */
    StarID previous();

    /**
     * Point the cursor to the first element of the data structure.
     */
    void goToStart();

    /**
     * Specifies whether the iterator can move to a random position.
     * @return
     */
    boolean canGoTo();

    /**
     * Go to the specified random position. Only use whenever canGoTo() returns true.
     * @param pos
     */
    void goTo(long pos);

    /**
     * Returns the number of estimated results of the Iterator.
     * It is usually more efficient than going through all the results.
     *
     * @return Number of estimated results.
     */
    long estimatedNumResults();

    /**
     * Returns the accuracy of the estimation of number of results as returned
     * by estimatedNumResults()
     *
     * @return
     */
    ResultEstimationType numResultEstimation();

    /**
     * Return the order in which the triples are iterated (Might be unknown)
     * @return
     */
    TripleComponentOrder getOrder();
}

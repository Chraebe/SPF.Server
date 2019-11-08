package org.rdfhdt.hdt.stars;

import org.rdfhdt.hdt.enums.ResultEstimationType;

import java.util.Iterator;

public interface IteratorStarString extends Iterator<StarString> {
    /**
     * Point the cursor to the first element of the data structure.
     */
    void goToStart();

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
}

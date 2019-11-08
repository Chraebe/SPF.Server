package org.linkeddatafragments.datasource;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.*;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.binding.Binding;
import org.linkeddatafragments.util.TripleElement;
import org.rdfhdt.hdt.enums.TripleComponentRole;
import org.rdfhdt.hdt.hdt.HDTManager;
import org.rdfhdt.hdt.hdt.impl.HDTPpImpl;
import org.rdfhdt.hdt.stars.IteratorStarID;
import org.rdfhdt.hdt.stars.StarID;
import org.rdfhdt.hdt.stars.StarString;
import org.rdfhdt.hdt.triples.IteratorTripleID;
import org.rdfhdt.hdt.triples.TripleID;
import org.rdfhdt.hdt.triples.impl.BitmapTriplesPp;
import org.rdfhdt.hdt.util.Tuple;
import org.rdfhdt.hdtjena.NodeDictionary;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class HdtPPDataSource extends HdtDataSource {

    public HdtPPDataSource(String title, String description, String hdtFile)
            throws IOException {
        super(title, description, hdtFile, true);
    }

    public TriplePatternFragment getFragment(StarString star, final long offset,
                                             final long limit) {
        if (offset < 0) {
            throw new IndexOutOfBoundsException("offset");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit");
        }

        StarID starid = star.toStarID(datasource.getDictionary());


        final Model triples = ModelFactory.createDefaultModel();
        final IteratorStarID matches = ((BitmapTriplesPp) datasource.getTriples())
                .search(starid);
        final boolean hasMatches = matches.hasNext();

        if (hasMatches) {
            boolean atOffset;

            for (int i = 0; !(atOffset = i == offset)
                    && matches.hasNext(); i++)
                matches.next();

            // try to add `limit` triples to the result model
            // todo Check if this is correct or it should be list of models instead
            if (atOffset) {
                for (int i = 0; i < limit && matches.hasNext(); i++) {
                    List<Triple> tpls = toTriples(matches.next());
                    int sz = tpls.size();
                    for(int j = 0; j < sz; j++) {
                        triples.add(triples.asStatement(tpls.get(j)));
                    }
                }
            }
        }

        // estimates can be wrong; ensure 0 is returned if there are no results,
        // and always more than actual results
        final long estimatedTotal = triples.size() > 0
                ? Math.max(offset + triples.size() + 1,
                matches.estimatedNumResults())
                : hasMatches ? Math.max(matches.estimatedNumResults(), 1) : 0;

        // create the fragment
        return new TriplePatternFragment() {
            @Override
            public Model getTriples() {
                return triples;
            }

            @Override
            public long getTotalSize() {
                return estimatedTotal;
            }
        };
    }

    public TriplePatternFragment getBindingFragment(
            final StarString star, final long offset, final long limit,
            final List<Binding> bindings, final List<Var> foundVariables)
    {
        return getBindingFragmentByTriplePatternSubstitution(
                // return getBindingFragmentByTestingHdtMatches(
                star, offset, limit, bindings, foundVariables);
    }

    public TriplePatternFragment getBindingFragmentByTriplePatternSubstitution(
            final StarString star, final long offset, final long limit,
            final List<Binding> bindings, final List<Var> foundVariables)
    {
        final StarIDIterator it = new StarIDIterator(
                bindings, star);

        final Model triples = ModelFactory.createDefaultModel();
        int triplesCheckedSoFar = 0;
        int triplesAddedInCurrentPage = 0;
        int countBindingsSoFar = 0;
        while ( it.hasNext() )
        {
            final StarID t = it.next();
            final IteratorStarID matches = ((BitmapTriplesPp) datasource.getTriples())
                    .search(t);
            final boolean hasMatches = matches.hasNext();
            if (hasMatches) {
                boolean atOffset;

                for (int i = 0; !(atOffset = i == offset)
                        && matches.hasNext(); i++)
                    matches.next();

                // try to add `limit` triples to the result model
                // todo Check if this is correct or it should be list of models instead
                if (atOffset) {
                    for (int i = 0; i < limit && matches.hasNext(); i++) {
                        List<Triple> tpls = toTriples(matches.next());
                        int sz = tpls.size();
                        for(int j = 0; j < sz; j++) {
                            triples.add(triples.asStatement(tpls.get(j)));
                        }
                    }
                }
            }
            countBindingsSoFar++;
        }

        final int bindingsSize = bindings.size();
        final long minimumTotal = offset + triplesAddedInCurrentPage + 1;
        final long estimatedTotal;
        if (triplesAddedInCurrentPage < limit)
        {
            estimatedTotal = offset + triplesAddedInCurrentPage;
        }
//         else // This else block is for testing purposes only. The next else block is the correct one.
//         {
//             estimatedTotal = minimumTotal;
//         }
        else
        {
            final int THRESHOLD = 10;
            final int maxBindingsToUseInEstimation;
            if (bindingsSize <= THRESHOLD)
            {
                maxBindingsToUseInEstimation = bindingsSize;
            } else{
                maxBindingsToUseInEstimation = THRESHOLD;
            }

            long estimationSum = 0L;
            it.reset();
            int i = 0;
            while ( it.hasNext() && i < maxBindingsToUseInEstimation )
            {
                i++;
                estimationSum += estimateResultSetSize( it.next() );
            }

            if (bindingsSize <= THRESHOLD)
            {
                if ( estimationSum <= minimumTotal )
                    estimatedTotal = minimumTotal;
                else
                    estimatedTotal = estimationSum;
            }
            else // bindingsSize > THRESHOLD
            {
                final double fraction = bindingsSize / maxBindingsToUseInEstimation;
                final double estimationAsDouble = fraction * estimationSum;
                final long estimation = Math.round(estimationAsDouble);
                if ( estimation <= minimumTotal )
                    estimatedTotal = minimumTotal;
                else
                    estimatedTotal = estimation;
            }
        }

        final long estimatedValid = estimatedTotal;

        return new TriplePatternFragment()
        {
            @Override
            public Model getTriples()
            {
                return triples;
            }

            @Override
            public long getTotalSize()
            {
                // return estimatedpageTotal;
                return estimatedValid;
            }

        };
    }

    protected long estimateResultSetSize( final StarID t )
    {
        final IteratorStarID matches = ((BitmapTriplesPp) datasource.getTriples())
            .search(t);

        if ( matches.hasNext() )
            return Math.max( matches.estimatedNumResults(), 1L );
        else
            return 0L;
    }

    /**
     * Converts the HDT++ triples to Jena Triples.
     *
     * @param starId
     *            the HDT star
     * @return the Jena triple
     */
    private List<Triple> toTriples(StarID starId)
    {
        List<Triple> lst = new ArrayList<>();
        Node subj = dictionary.getNode(starId.getSubject(), TripleComponentRole.SUBJECT);
        List<Tuple<Integer, Integer>> tpls = starId.getTriples();
        int sz = tpls.size();
        for(int i = 0; i < sz; i++) {
            Tuple<Integer, Integer> tpl = tpls.get(i);
            lst.add(new Triple(subj,
                    dictionary.getNode(tpl.x,
                            TripleComponentRole.PREDICATE),
                    dictionary.getNode(tpl.y,
                            TripleComponentRole.OBJECT)));
        }

        return lst;
    }

    private class StarIDIterator implements Iterator<StarID> {
        private List<Binding> bindings;
        private StarString star;
        private int current = 0;
        private StarID next = null;

        StarIDIterator(List<Binding> bindings, StarString star) {
            this.bindings = bindings;
            this.star = star;
        }

        private void bufferNext() {
            if(current >= bindings.size()) {
                next = null;
                return;
            }
            Binding binding = bindings.get(current);
            current++;
            StarString s = new StarString(star.getSubject(), star.getTriples());

            Iterator<Var> vars = binding.vars();
            while(vars.hasNext()) {
                Var var = vars.next();
                Node node = binding.get(var);

                String val = "";
                if(node.isLiteral())
                    val = node.getLiteral().toString();
                else if(node.isURI())
                    val = node.getURI();

                s.updateField(var.getVarName(), val);
            }

            next = s.toStarID(datasource.getDictionary());
        }

        public void reset() {
            current = 0;
        }

        @Override
        public boolean hasNext() {
            if(next == null)
                bufferNext();
            return next != null;
        }

        @Override
        public StarID next() {
            StarID n = next;
            next = null;
            return n;
        }
    }
}

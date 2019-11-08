package org.rdfhdt.hdt.triples.impl;

import org.rdfhdt.hdt.compact.bitmap.Bitmap;
import org.rdfhdt.hdt.compact.sequence.Sequence;
import org.rdfhdt.hdt.enums.ResultEstimationType;
import org.rdfhdt.hdt.enums.TripleComponentOrder;
import org.rdfhdt.hdt.triples.IteratorTripleID;
import org.rdfhdt.hdt.triples.TripleID;

import java.util.List;

public class BitmapTriplesIteratorPp implements IteratorTripleID {
    private final BitmapTriplesPp triples;
    private final List<Long> families;
    private final long subject;
    private final long predicate;
    private final long object;
    private final Bitmap bmap;
    private final Sequence seq;
    private final long estimatedSize;

    private TripleID next = null;
    private long ptr = 0, family = 0, start = 0, end = 0, snum = 1, s = 0, sid = 0;
    private int i = 0;
    private boolean newfam = true, news = true;

    public BitmapTriplesIteratorPp(BitmapTriplesPp triples, Bitmap bmap, Sequence seq, List<Long> families, TripleID pattern) {
        this.triples = triples;
        this.families = families;
        this.bmap = bmap;
        this.seq = seq;
        subject = pattern.getSubject();
        predicate = pattern.getPredicate();
        object = pattern.getObject();

        long size = 0;

        for(Long family : families) {
            size += (triples.seqFirst.get(family)-triples.seqFirst.get(family-1));
        }

        estimatedSize = size;
    }

    @Override
    public boolean hasPrevious() {
        return false;
    }

    @Override
    public TripleID previous() {
        return null;
    }

    @Override
    public void goToStart() {
    }

    @Override
    public boolean canGoTo() {
        return false;
    }

    @Override
    public void goTo(long pos) {
    }

    @Override
    public long estimatedNumResults() {
        return estimatedSize;
    }

    @Override
    public ResultEstimationType numResultEstimation() {
        return null;
    }

    @Override
    public TripleComponentOrder getOrder() {
        return null;
    }

    @Override
    public boolean hasNext() {
        bufferNext();
        return next != null;
    }

    @Override
    public TripleID next() {
        if(next == null)
            bufferNext();
        return next;
    }

    private void bufferNext() {
        if(i >= families.size()) {
            next = null;
            return;
        }

        if(newfam) {
            family = families.get(i);
            start = triples.seqFirst.get(family-1);
            if(family > triples.seqF.getNumberOfElements()) {
                end = triples.permS.getNumberOfElements();
            } else {
                end = triples.seqFirst.get(family);
            }
            newfam = false;
            s = start;
            snum = 1;
        }

        if(s >= end) {
            newfam = true;
            i++;
            bufferNext();
            return;
        }

        if(news) {
            sid = triples.getSubjIDPermS(snum, family);
            news = false;
        }

        boolean found = false;
        if(subject == 0 || sid == subject) {
            long oid = triples.getObjIDPermO(seq.get(ptr), predicate);

            if(object == 0 || object == oid) {
                next = new TripleID((int) sid, (int) predicate, (int) oid);
                found = true;
            }
        }

        ptr++;
        if(bmap.access(ptr) || ptr >= bmap.getNumBits()) {
            snum++;
            s++;
            news = true;
        }

        if(!found) {
            bufferNext();
        }
    }
}

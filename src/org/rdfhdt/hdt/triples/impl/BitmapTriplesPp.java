package org.rdfhdt.hdt.triples.impl;

import org.rdfhdt.hdt.compact.bitmap.AdjacencyList;
import org.rdfhdt.hdt.compact.bitmap.Bitmap;
import org.rdfhdt.hdt.compact.bitmap.BitmapFactory;
import org.rdfhdt.hdt.compact.bitmap.ModifiableBitmap;
import org.rdfhdt.hdt.compact.sequence.DynamicSequence;
import org.rdfhdt.hdt.compact.sequence.Sequence;
import org.rdfhdt.hdt.compact.sequence.SequenceFactory;
import org.rdfhdt.hdt.dictionary.Dictionary;
import org.rdfhdt.hdt.dictionary.DictionarySection;
import org.rdfhdt.hdt.enums.TripleComponentOrder;
import org.rdfhdt.hdt.exceptions.IllegalFormatException;
import org.rdfhdt.hdt.exceptions.NotImplementedException;
import org.rdfhdt.hdt.hdt.HDTVocabulary;
import org.rdfhdt.hdt.header.Header;
import org.rdfhdt.hdt.listener.ProgressListener;
import org.rdfhdt.hdt.options.ControlInfo;
import org.rdfhdt.hdt.options.ControlInformation;
import org.rdfhdt.hdt.options.HDTOptions;
import org.rdfhdt.hdt.options.HDTSpecification;
import org.rdfhdt.hdt.stars.IteratorStarID;
import org.rdfhdt.hdt.stars.StarID;
import org.rdfhdt.hdt.triples.IteratorTripleID;
import org.rdfhdt.hdt.triples.TempTriples;
import org.rdfhdt.hdt.triples.TripleID;
import org.rdfhdt.hdt.triples.TriplesPrivate;
import org.rdfhdt.hdt.util.BitUtil;
import org.rdfhdt.hdt.util.Tuple;
import org.rdfhdt.hdt.util.io.CountInputStream;
import org.rdfhdt.hdt.util.listener.IntermediateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

public class BitmapTriplesPp implements TriplesPrivate {
    private static final Logger log = LoggerFactory.getLogger(BitmapTriples.class);
    private final HDTOptions spec;

    protected TripleComponentOrder order = TripleComponentOrder.SPO;

    protected Sequence seqF, seqT, permS, seqFirst, permO;
    protected Bitmap bitmapF, bitmapT, bitmapPermO;
    protected AdjacencyList adjF, adjT, adjPermO;

    protected List<Bitmap> bitmapsO = new ArrayList<>();
    protected List<Sequence> seqsO = new ArrayList<>();
    protected List<AdjacencyList> adjsO = new ArrayList<>();

    // Index for Y
    public PredicateIndex predicateIndex;

    private boolean isClosed = false;
    private long numPreds = 0;

    public BitmapTriplesPp() {
        this(new HDTSpecification());
    }

    public BitmapTriplesPp(HDTOptions spec) {
        this.spec = spec;
        String orderStr = spec.get("triplesOrder");
        if (orderStr != null) {
            order = TripleComponentOrder.valueOf(orderStr);
        }

        //bitmapO = BitmapFactory.createBitmap(spec.get("bitmap.o"));
        bitmapF = BitmapFactory.createBitmap(spec.get("bitmap.f"));
        bitmapT = BitmapFactory.createBitmap(spec.get("bitmap.t"));
        bitmapPermO = BitmapFactory.createBitmap(spec.get("bitmap.perm.o"));

        //seqO = SequenceFactory.createStream(spec.get("seq.o"));
        seqF = SequenceFactory.createStream(spec.get("seq.f"));
        seqT = SequenceFactory.createStream(spec.get("seq.t"));
        seqFirst = SequenceFactory.createStream(spec.get("seq.first"));
        permS = SequenceFactory.createStream(spec.get("perm.s"));
        permO = SequenceFactory.createStream(spec.get("perm.o"));

        //adjO = new AdjacencyList(seqO, bitmapO);
        adjF = new AdjacencyList(seqF, bitmapF);
        adjT = new AdjacencyList(seqT, bitmapT);
        adjPermO = new AdjacencyList(permO, bitmapPermO);

        isClosed = false;
    }

    public BitmapTriplesPp(BitmapTriples triples, Dictionary dict) {
        this();

        Bitmap z = triples.bitmapZ;
        Bitmap y = triples.bitmapY;
        Sequence sz = triples.seqZ;
        Sequence sy = triples.seqY;

        ((DynamicSequence) seqF).setNumBits(sy.getNumBits());
        //((DynamicSequence) seqO).setNumBits(sz.getNumBits());
        ((DynamicSequence) seqT).setNumBits(sz.getNumBits());


        DictionarySection section = dict.getPredicates();
        long tid = section.locate("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");

        // Creating families
        List<Long> family = new ArrayList<>();
        List<Long> types = new ArrayList<>();
        int num = 1;
        long zval = 0;
        for (long i = 0; i < y.getNumBits(); i++) {
            long pid = sy.get(i);
            if (pid == tid) {
                while (!z.access(zval)) {
                    types.add(sz.get(zval));
                    zval++;
                }
                types.add(sz.get(zval));
                zval++;
                //continue;
            } else {
                while (!z.access(zval)) {
                    zval++;
                }
                zval++;
            }

            if (!family.contains(sy.get(i)))
                family.add(sy.get(i));
            if (y.access(i)) {
                System.out.print("\rChecking subject " + num + "/" + y.countOnes());
                num++;
                if (getFamilyNum(family) == 0) {
                    addFamily(family);
                    addTypes(types);
                }
                family = new ArrayList<>();
                types = new ArrayList<>();
            }
        }
        System.out.println("Created " + bitmapF.countOnes() + " families and " + bitmapT.countOnes() + " types.");

        // Create predicate sequence
        int numbits = BitUtil.log2(bitmapF.countOnes());
        ((DynamicSequence) permS).setNumBits(numbits);

        num = 1;
        family = new ArrayList<>();
        for (long i = 0; i < y.getNumBits(); i++) {
            if (!family.contains(sy.get(i)))
                family.add(sy.get(i));
            if (y.access(i)) {
                System.out.print("\rChecking subject " + num + "/" + y.countOnes());
                num++;
                addToFamilies(getFamilyNum(family));
                family = new ArrayList<>();
            }
        }

        numbits = BitUtil.log2(y.countOnes());
        ((DynamicSequence) seqFirst).setNumBits(numbits);
        int count = 0;

        List<Bitmap> bFamilies = new ArrayList<>();
        List<Sequence> sFamilies = new ArrayList<>();

        long currentFamily = 0;
        long currentSubject = 1;
        long currentFamilyIndex = 0;
        for (long i = 0; i < bitmapF.countOnes(); i++) {
            Bitmap bmap = BitmapFactory.createBitmap(spec.get("bitmap.tmp" + i));
            Sequence seq = SequenceFactory.createStream(spec.get("sequence.tmp" + i));
            ((DynamicSequence) seq).setNumBits(sz.getNumBits());

            currentSubject = 1;
            ((DynamicSequence) seqFirst).append(count + 1);

            for (long j = 0; j < z.getNumBits(); j++) {
                if (currentFamily == 0) {
                    currentFamily = permS.get(currentSubject - 1);
                    if (currentFamily == i + 1) count++;
                    currentFamilyIndex = findStartIndexFamily(currentFamily);
                }

                if (currentFamily == i + 1) {
                    ((ModifiableBitmap) bmap).append(z.access(j));
                    ((DynamicSequence) seq).append(sz.get(j));
                }

                if (z.access(j)) {
                    if (bitmapF.access(currentFamilyIndex)) {
                        currentSubject++;
                        currentFamily = 0;
                        currentFamilyIndex = 0;
                        continue;
                    }
                    currentFamilyIndex++;
                }
            }

            bFamilies.add(bmap);
            sFamilies.add(seq);
        }

        List<List<Bitmap>> bFam = new ArrayList<>();
        List<List<Sequence>> sFam = new ArrayList<>();

        for (int i = 0; i < bFamilies.size(); i++) {
            List<Bitmap> bl = new ArrayList<>();
            List<Sequence> sl = new ArrayList<>();
            Bitmap b = bFamilies.get(i);
            Sequence s = sFamilies.get(i);

            long fcount = 0;
            if (i == seqFirst.getNumberOfElements() - 1) {
                fcount = (permS.getNumberOfElements() + 1) - seqFirst.get(i);
            } else {
                fcount = seqFirst.get(i + 1) - seqFirst.get(i);
            }

            int cnt = 1;
            long current = findStartIndexFamily(i + 1);
            while (!bitmapF.access(current)) {
                cnt++;
                current++;
            }

            int seen;
            for (int j = 0; j < cnt; j++) {
                Bitmap bmap = BitmapFactory.createBitmap(spec.get("bitmap.t" + i));
                Sequence seq = SequenceFactory.createStream(spec.get("sequence.t" + i));
                ((DynamicSequence) seq).setNumBits(sz.getNumBits());

                for (int k = 0; k < fcount; k++) {
                    current = 0;
                    seen = 0;

                    while (seen < (k * cnt)) {
                        if (b.access(current)) {
                            seen++;
                        }
                        current++;
                    }

                    seen = 0;
                    while (seen < j) {
                        if (b.access(current)) {
                            seen++;
                        }
                        current++;
                    }
                    while (!b.access(current)) {
                        ((ModifiableBitmap) bmap).append(b.access(current));
                        ((DynamicSequence) seq).append(s.get(current));
                        current++;
                    }
                    ((ModifiableBitmap) bmap).append(b.access(current));
                    ((DynamicSequence) seq).append(s.get(current));
                }
                bl.add(bmap);
                sl.add(seq);
            }

            bFam.add(bl);
            sFam.add(sl);
        }

        bFamilies.clear();
        sFamilies.clear();

        // Finding amount of predicates
        long max = 0;
        for (long i = 0; i < seqF.getNumberOfElements(); i++) {
            long x = seqF.get(i);
            if (x > max) max = x;
        }

        List<Bitmap> bPred = new ArrayList<>();
        List<Sequence> sPred = new ArrayList<>();
        for (long i = 0; i < max; i++) {
            long prednum = i + 1;
            Bitmap bmap = BitmapFactory.createBitmap(spec.get("bmap.t" + i));
            Sequence seq = SequenceFactory.createStream(spec.get("seq.t" + i));
            ((DynamicSequence) seq).setNumBits(sz.getNumBits());

            int famnum = 0;
            int numpred = 0;
            for (long j = 0; j < bitmapF.getNumBits(); j++) {
                if (prednum == seqF.get(j)) {
                    Bitmap b = bFam.get(famnum).get(numpred);
                    Sequence s = sFam.get(famnum).get(numpred);

                    for (long k = 0; k < b.getNumBits(); k++) {
                        ((ModifiableBitmap) bmap).append(b.access(k));
                        ((DynamicSequence) seq).append(s.get(k));
                    }
                }
                if (bitmapF.access(j)) {
                    famnum++;
                    numpred = 0;
                } else {
                    numpred++;
                }
            }
            bPred.add(bmap);
            sPred.add(seq);
        }

        bFam.clear();
        sFam.clear();

        System.out.println("Creating permO");

        long numobj = 0;
        for (long i = 0; i < sz.getNumberOfElements(); i++) {
            long v = sz.get(i);
            if (v > numobj) numobj = v;
        }

        ((DynamicSequence) permO).setNumBits(sz.getNumBits());
        for (long i = 0; i < numobj; i++) {
            if (i % 1000 == 0)
                System.out.println(i + " of " + numobj);
            boolean first = true;
            boolean any = false;
            for (int j = 0; j < sPred.size(); j++) {
                Sequence s = sPred.get(j);
                for (long k = 0; k < s.getNumberOfElements(); k++) {
                    if (s.get(k) == i) {
                        any = true;
                        if (!first) {
                            ((ModifiableBitmap) bitmapPermO).append(false);
                        } else {
                            first = false;
                        }
                        ((DynamicSequence) permO).append(j + 1);
                        break;
                    }
                }
            }
            if (any)
                ((ModifiableBitmap) bitmapPermO).append(true);
        }

        //List<Bitmap> bitmapsO = new ArrayList<>();
        //List<Sequence> seqsO = new ArrayList<>();

        for (int i = 0; i < bPred.size(); i++) {
            Bitmap b = bPred.get(i);
            Sequence s = sPred.get(i);

            int cnt = 0;
            Set<Long> ids = new HashSet<>();
            for (long j = 0; j < s.getNumberOfElements(); j++) {
                long l = s.get(j);
                if (!ids.contains(l)) {
                    cnt++;
                    ids.add(l);
                }
            }

            List<Long> idList = new ArrayList<>(ids);
            idList.sort(null);

            Bitmap bmap = BitmapFactory.createBitmap(spec.get("bitmap.o." + i));
            Sequence seq = SequenceFactory.createStream(spec.get("seq.o." + i));
            ((DynamicSequence) seq).setNumBits(BitUtil.log2(cnt));

            for (long j = 0; j < b.getNumBits(); j++) {
                ((ModifiableBitmap) bmap).append(b.access(j));
                int index = idList.indexOf(s.get(j)) + 1;
                ((DynamicSequence) seq).append(index);
            }

            bitmapsO.add(bmap);
            seqsO.add(seq);
            adjsO.add(new AdjacencyList(seq, bmap));
        }

        bPred.clear();
        sPred.clear();

        numPreds = seqsO.size();

        System.out.println(bitmapsO);

        //numbits = BitUtil.log2(maxval);
        //((DynamicSequence) seqO).setNumBits(numbits);

        /*for (int i = 0; i < bitmapsO.size(); i++) {
            Bitmap b = bitmapsO.get(i);
            Sequence s = seqsO.get(i);

            for (long j = 0; j < b.getNumBits(); j++) {
                ((ModifiableBitmap) bitmapO).append(b.access(j));
                ((DynamicSequence) seqO).append(s.get(j));
            }
        }

        bitmapsO.clear();
        seqsO.clear();*/
    }

    public long getNumPreds() {
        return numPreds;
    }

    protected long findStartIndexFamily(long fnum) {
        long index = 0;

        long count = 1;
        for (long i = 0; i < bitmapF.getNumBits(); i++) {
            if (count >= fnum) break;

            index++;
            if (bitmapF.access(i)) count++;
        }

        return index;
    }


    private void addToFamilies(Long f) {
        ((DynamicSequence) permS).append(f);
    }

    private void addTypes(List<Long> f) {
        if (f.size() == 0) {
            ((DynamicSequence) seqT).append(0);
            ((ModifiableBitmap) bitmapT).append(true);
            return;
        }

        for (int i = 0; i < (f.size() - 1); i++) {
            ((DynamicSequence) seqT).append(f.get(i));
            ((ModifiableBitmap) bitmapT).append(false);
        }
        ((DynamicSequence) seqT).append(f.get(f.size() - 1));
        ((ModifiableBitmap) bitmapT).append(true);
    }

    private void addFamily(List<Long> f) {
        for (int i = 0; i < (f.size() - 1); i++) {
            ((DynamicSequence) seqF).append(f.get(i));
            ((ModifiableBitmap) bitmapF).append(false);
        }
        ((DynamicSequence) seqF).append(f.get(f.size() - 1));
        ((ModifiableBitmap) bitmapF).append(true);
    }

    private long getFamilyNum(List<Long> f) {
        int num = 0;
        long fnum = 1;

        if (bitmapF.getNumBits() == 0)
            return 0;

        //System.out.println(bitmapF.getNumBits() + " " + seqF.getNumberOfElements());
        //System.out.println(bitmapF);
        //System.out.println(tempSeqF);

        for (int i = 0; i < bitmapF.getNumBits(); i++) {
            if (num + 1 > f.size() || i + 1 > seqF.getNumberOfElements() || !(f.get(num).equals(seqF.get(i)))) {
                num = 0;
                while (!bitmapF.access(i))
                    i++;
                fnum++;
                continue;
            }

            if (bitmapF.access(i) && (num + 1) == f.size())
                return fnum;
            else if (bitmapF.access(i)) {
                num = 0;
                fnum++;
                continue;
            }

            num++;
        }

        return 0;
    }

    public void load(IteratorTripleID it, ProgressListener listener) {
        throw new NotImplementedException("An HDT++ file cannot be modified in this version.");
    }

    /* (non-Javadoc)
     * @see hdt.triples.Triples#load(hdt.triples.TempTriples, hdt.ProgressListener)
     */
    @Override
    public void load(TempTriples triples, ProgressListener listener) {
        throw new NotImplementedException("An HDT++ file cannot be modified in this version.");
    }

    /* (non-Javadoc)
     * @see hdt.triples.Triples#search(hdt.triples.TripleID)
     */
    @Override
    public IteratorTripleID search(TripleID pattern) {
        if (isClosed) {
            throw new IllegalStateException("Cannot search on BitmapTriples if it's already closed");
        }

        if (getNumberOfElements() == 0 || pattern.isNoMatch()) {
            return new EmptyTriplesIterator(order);
        }

        long predicate = pattern.getPredicate();

        Bitmap bmap = bitmapsO.get((int)predicate-1);
        Sequence seq = seqsO.get((int)predicate-1);

        // Finding families
        List<Long> families = new ArrayList<>();

        long fam = 1;
        for (long i = 0; i < bitmapF.getNumBits(); i++) {
            if(seqF.get(i) == predicate) families.add(fam);
            if(bitmapF.access(i)) fam++;
        }

        return new BitmapTriplesIteratorPp(this, bmap, seq, families, pattern);
    }

    /* (non-Javadoc)
     * @see hdt.triples.Triples#search(hdt.triples.TripleID)
     */
    public IteratorStarID search(StarID pattern) {
        if (isClosed) {
            throw new IllegalStateException("Cannot search on BitmapTriples if it's already closed");
        }

        Map<Integer, Set<Long>> familyMap = new HashMap<>();

        List<Integer> preds = pattern.getPredicates();
        for(Integer p : preds) {
            familyMap.put(p, new HashSet<>());
        }
        long fam = 1;
        for (long i = 0; i < bitmapF.getNumBits(); i++) {
            for(Integer p : preds) {
                if(seqF.get(i) == p) familyMap.get(p).add(fam);
            }
            if(bitmapF.access(i)) fam++;
        }

        Set<Long> families = new HashSet<>();
        for(Set<Long> e : familyMap.values()) {
            if(families.size() == 0) {
                families.addAll(e);
            } else {
                families.retainAll(e);
            }
        }

        familyMap.clear();
        Map<Long, Tuple<Bitmap, Sequence>> data = new HashMap<>();
        for(Integer i : pattern.getPredicates()) {
            data.put((long)i, new Tuple<>(bitmapsO.get(i-1), seqsO.get(i-1)));
        }

        return new BitmapStarIterator(this, pattern, new ArrayList<>(families), data);
    }

    protected long getObjIDPermO(long onum, long pnum) {
        long id = 1;

        long found = 0;
        for(long i = 0; i < permO.getNumberOfElements(); i++) {
            if(permO.get(i) == pnum) found++;
            if(found == onum) break;
            if(bitmapPermO.access(i)) id++;
        }

        return id;
    }

    protected long getSubjIDPermS(long snum, long fnum) {
        long id = 1;

        long found = 0;
        for(long i = 0; i < permS.getNumberOfElements(); i++) {
            if(permS.get(i) == fnum) found++;
            if(found == snum) break;
            id++;
        }

        return id;
    }

    /* (non-Javadoc)
     * @see hdt.triples.Triples#searchAll()
     */
    @Override
    public IteratorTripleID searchAll() {
        return this.search(new TripleID());
    }

    /* (non-Javadoc)
     * @see hdt.triples.Triples#getNumberOfElements()
     */
    @Override
    public long getNumberOfElements() {
        if (isClosed) return 0;
        long count = 0;
        for(Sequence s : seqsO) {
            count += s.getNumberOfElements();
        }

        return count;
    }

    /* (non-Javadoc)
     * @see hdt.triples.Triples#size()
     */
    @Override
    public long size() {
        if (isClosed) return 0;
        long cs = 0;
        for(Sequence s : seqsO) {
            cs += s.size();
        }
        long bs = 0;
        for(Bitmap b : bitmapsO) {
            bs += b.getSizeBytes();
        }

        return cs + seqF.size() + seqT.size() + permS.size() + bs + bitmapF.getSizeBytes() + bitmapT.getSizeBytes();
    }

    /* (non-Javadoc)
     * @see hdt.triples.Triples#save(java.io.OutputStream, hdt.ControlInfo, hdt.ProgressListener)
     */
    @Override
    public void save(OutputStream output, ControlInfo ci, ProgressListener listener) throws IOException {
        ci.clear();
        ci.setFormat(getType());
        ci.setInt("order", order.ordinal());
        ci.setType(ControlInfo.Type.TRIPLES);
        ci.save(output);

        //seqO, seqF, seqT, permS, seqFirst, permO;
        //bitmapO, bitmapF, bitmapT, bitmapPermO;

        IntermediateListener iListener = new IntermediateListener(listener);
        Sequence sz = SequenceFactory.createStream("seq.preds");
        ((DynamicSequence) sz).setNumBits(BitUtil.log2(numPreds));
        ((DynamicSequence) sz).append(numPreds);

        sz.save(output, iListener);
        //bitmapO.save(output, iListener);
        bitmapF.save(output, iListener);
        bitmapT.save(output, iListener);
        bitmapPermO.save(output, iListener);
        //seqO.save(output, iListener);
        seqF.save(output, iListener);
        seqT.save(output, iListener);
        permS.save(output, iListener);
        seqFirst.save(output, iListener);
        permO.save(output, iListener);

        for(int i = 0; i < numPreds; i++) {
            bitmapsO.get(i).save(output, iListener);
            seqsO.get(i).save(output, iListener);
        }
    }

    /* (non-Javadoc)
     * @see hdt.triples.Triples#load(java.io.InputStream, hdt.ControlInfo, hdt.ProgressListener)
     */
    @Override
    public void load(InputStream input, ControlInfo ci, ProgressListener listener) throws IOException {
        if (ci.getType() != ControlInfo.Type.TRIPLES) {
            throw new IllegalFormatException("Trying to read a triples section, but was not triples.");
        }

        if (!ci.getFormat().equals(getType())) {
            throw new IllegalFormatException("Trying to read BitmapTriples, but the data does not seem to be BitmapTriples");
        }

        order = TripleComponentOrder.values()[(int) ci.getInt("order")];

        IntermediateListener iListener = new IntermediateListener(listener);
        Sequence sz = SequenceFactory.createStream(input);
        sz.load(input, iListener);
        numPreds = sz.get(0);

        //bitmapO = BitmapFactory.createBitmap(input);
        //bitmapO.load(input, iListener);

        bitmapF = BitmapFactory.createBitmap(input);
        bitmapF.load(input, iListener);

        bitmapT = BitmapFactory.createBitmap(input);
        bitmapT.load(input, iListener);

        bitmapPermO = BitmapFactory.createBitmap(input);
        bitmapPermO.load(input, iListener);

        //seqO = SequenceFactory.createStream(input);
        //seqO.load(input, iListener);

        seqF = SequenceFactory.createStream(input);
        seqF.load(input, iListener);

        seqT = SequenceFactory.createStream(input);
        seqT.load(input, iListener);

        permS = SequenceFactory.createStream(input);
        permS.load(input, iListener);

        seqFirst = SequenceFactory.createStream(input);
        seqFirst.load(input, iListener);

        permO = SequenceFactory.createStream(input);
        permO.load(input, iListener);

        for(long i = 0; i < numPreds; i++) {
            Bitmap b = BitmapFactory.createBitmap(input);
            b.load(input, iListener);

            Sequence s = SequenceFactory.createStream(input);
            s.load(input, iListener);

            bitmapsO.add(b);
            seqsO.add(s);
            adjsO.add(new AdjacencyList(s,b));
        }

        //adjO = new AdjacencyList(seqO, bitmapO);
        adjF = new AdjacencyList(seqF, bitmapF);
        adjT = new AdjacencyList(seqT, bitmapT);
        adjPermO = new AdjacencyList(permO, bitmapPermO);

        isClosed = false;
    }


    @Override
    public void mapFromFile(CountInputStream input, File f, ProgressListener listener) throws IOException {
        ControlInformation ci = new ControlInformation();
        ci.load(input);
        if (ci.getType() != ControlInfo.Type.TRIPLES) {
            throw new IllegalFormatException("Trying to read a triples section, but was not triples.");
        }

        if (!ci.getFormat().equals(getType())) {
            throw new IllegalFormatException("Trying to read BitmapTriplesPP, but the data does not seem to be BitmapTriplesPP");
        }

        order = TripleComponentOrder.values()[(int) ci.getInt("order")];

        IntermediateListener iListener = new IntermediateListener(listener);
        Sequence sz = SequenceFactory.createStream(input);
        sz.load(input, iListener);
        numPreds = sz.get(0);

        //bitmapO = BitmapFactory.createBitmap(input);
        //bitmapO.load(input, iListener);

        bitmapF = BitmapFactory.createBitmap(input);
        bitmapF.load(input, iListener);

        bitmapT = BitmapFactory.createBitmap(input);
        bitmapT.load(input, iListener);

        bitmapPermO = BitmapFactory.createBitmap(input);
        bitmapPermO.load(input, iListener);

        //seqO = SequenceFactory.createStream(input);
        //seqO.load(input, iListener);

        seqF = SequenceFactory.createStream(input);
        seqT = SequenceFactory.createStream(input);
        permS = SequenceFactory.createStream(input);
        seqFirst = SequenceFactory.createStream(input);
        permO = SequenceFactory.createStream(input);

        for(long i = 0; i < numPreds; i++) {
            Bitmap b = BitmapFactory.createBitmap(input);
            b.load(input, iListener);

            Sequence s = SequenceFactory.createStream(input);
            s.load(input, iListener);

            bitmapsO.add(b);
            seqsO.add(s);
            adjsO.add(new AdjacencyList(s,b));
        }

        //adjO = new AdjacencyList(seqO, bitmapO);
        adjF = new AdjacencyList(seqF, bitmapF);
        adjT = new AdjacencyList(seqT, bitmapT);
        adjPermO = new AdjacencyList(permO, bitmapPermO);

        isClosed = false;
    }

    @Override
    public void generateIndex(ProgressListener listener) {
        //todo implement
        throw new NotImplementedException();
    }

    /* (non-Javadoc)
     * @see hdt.triples.Triples#populateHeader(hdt.header.Header, java.lang.String)
     */
    @Override
    public void populateHeader(Header header, String rootNode) {
        if (rootNode == null || rootNode.length() == 0) {
            throw new IllegalArgumentException("Root node for the header cannot be null");
        }

        header.insert(rootNode, HDTVocabulary.TRIPLES_TYPE, getType());
        header.insert(rootNode, HDTVocabulary.TRIPLES_NUM_TRIPLES, getNumberOfElements());
        header.insert(rootNode, HDTVocabulary.TRIPLES_ORDER, order.toString());
    }

    /* (non-Javadoc)
     * @see hdt.triples.Triples#getType()
     */
    @Override
    public String getType() {
        return HDTVocabulary.TRIPLES_TYPE_BITMAP_PP;
    }

    /* (non-Javadoc)
     * @see hdt.triples.Triples#saveIndex(java.io.OutputStream, hdt.options.ControlInfo, hdt.listener.ProgressListener)
     */
    @Override
    public void saveIndex(OutputStream output, ControlInfo ci, ProgressListener listener) throws IOException {
        //todo implement
        throw new NotImplementedException();
    }

    /* (non-Javadoc)
     * @see hdt.triples.Triples#loadIndex(java.io.InputStream, hdt.options.ControlInfo, hdt.listener.ProgressListener)
     */
    @Override
    public void loadIndex(InputStream input, ControlInfo ci, ProgressListener listener) throws IOException {
        //todo implement
        throw new NotImplementedException();
    }

    @Override
    public void mapIndex(CountInputStream input, File f, ControlInfo ci, ProgressListener listener) throws IOException {
        //todo implement
        throw new NotImplementedException();
    }

    @Override
    public void close() throws IOException {
        isClosed = true;
        /*if (seqO != null) {
            seqO.close();
            seqO = null;
        }*/

        if (seqF != null) {
            seqF.close();
            seqF = null;
        }
        if (seqT != null) {
            seqT.close();
            seqT = null;
        }
        if (permO != null) {
            permO.close();
            permO = null;
        }
        if (permS != null) {
            permS.close();
            permS = null;
        }
        if (seqFirst != null) {
            seqFirst.close();
            seqFirst = null;
        }
        //todo Close indexes
        if (predicateIndex != null) {
            predicateIndex.close();
            predicateIndex = null;
        }
    }

    public TripleComponentOrder getOrder() {
        return this.order;
    }
}

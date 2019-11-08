package org.rdfhdt.hdt.stars;

import org.rdfhdt.hdt.dictionary.Dictionary;
import org.rdfhdt.hdt.enums.TripleComponentRole;
import org.rdfhdt.hdt.triples.TripleID;
import org.rdfhdt.hdt.util.Tuple;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class StarID implements Comparable<StarID>, Serializable {
    private static final long serialVersionUID = -4685524566493494912L;

    private int subject;
    private List<Tuple<Integer, Integer>> triples = new ArrayList<>();

    /**
     * Basic constructor
     */
    public StarID() {
        super();
    }

    public StarID(int subject, List<Tuple<Integer, Integer>> triples) {
        this.subject = subject;
        this.triples = triples;
    }

    public StarID(int subject) {
        this.subject = subject;
    }

    /**
     * Build a TripleID as a copy of another one.
     * @param other
     */
    public StarID(StarID other) {
        super();
        subject = other.subject;
        triples = new ArrayList<>(other.triples);
    }

    public int size() {
        return triples.size();
    }

    public TripleID getTriple(int pos) {
        Tuple<Integer, Integer> t = triples.get(pos);
        return new TripleID(subject, t.x, t.y);
    }

    public List<Integer> getPredicates() {
        List<Integer> ret = new ArrayList<>();

        for(Tuple<Integer, Integer> t : triples) {
            ret.add(t.x);
        }

        return ret;
    }

    public int getSubject() {
        return subject;
    }

    public void setSubject(int subject) {
        this.subject = subject;
    }

    public void setObject(int predicate, int object) {
        boolean contains = false;

        for(Tuple<Integer, Integer> t : triples) {
            if(t.x == predicate) {
                contains = true;
                t.y = object;
                break;
            }
        }

        if(!contains) {
            triples.add(new Tuple<>(predicate, object));
        }
    }

    public List<Tuple<Integer, Integer>> getTriples() {
        return triples;
    }

    public void setTriples(List<Tuple<Integer, Integer>> triples) {
        this.triples = triples;
    }

    public void addTriple(Tuple<Integer, Integer> triple) {
        triples.add(triple);
    }

    public StarString toStarString(Dictionary dictionary) {
        CharSequence subj = dictionary.idToString(subject, TripleComponentRole.SUBJECT);
        List<Tuple<CharSequence, CharSequence>> lst = new ArrayList<>();
        int size = size();
        for(int i = 0; i < size; i++) {
            TripleID tpl = getTriple(i);
            Tuple<CharSequence, CharSequence> t = new Tuple<>(
                    dictionary.idToString(tpl.getPredicate(), TripleComponentRole.PREDICATE),
                    dictionary.idToString(tpl.getObject(), TripleComponentRole.OBJECT)
            );
            lst.add(t);
        }

        return new StarString(subj, lst);
    }

    @Override
    public int compareTo(StarID other) {
        int result = this.subject - other.subject;

        if(result==0) {
            return triples.hashCode() - other.triples.hashCode();
        }
        return result;
    }

    @Override
    public String toString() {
        String str = subject + "";

        for(Tuple<Integer, Integer> t : triples) {
            str += "\n    " + t.x + " " + t.y;
        }

        return str;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StarID starID = (StarID) o;
        return subject == starID.subject &&
                Objects.equals(triples, starID.triples);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, triples);
    }

    /**
     * Set all components to zero.
     */
    public void clear() {
        triples.clear();
        subject = 0;
    }
}

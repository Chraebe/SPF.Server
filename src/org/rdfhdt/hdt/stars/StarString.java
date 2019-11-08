package org.rdfhdt.hdt.stars;

import org.rdfhdt.hdt.dictionary.Dictionary;
import org.rdfhdt.hdt.enums.TripleComponentRole;
import org.rdfhdt.hdt.triples.TripleString;
import org.rdfhdt.hdt.util.Tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class StarString {
    private CharSequence subject;
    private List<Tuple<CharSequence, CharSequence>> triples = new ArrayList<>();

    /**
     * Basic constructor
     */
    public StarString() {
        super();
    }

    public StarString(CharSequence subject, List<Tuple<CharSequence, CharSequence>> triples) {
        this.subject = subject;
        this.triples = triples;
    }

    public StarString(CharSequence subject) {
        this.subject = subject;
    }

    /**
     * Build a TripleID as a copy of another one.
     * @param other
     */
    public StarString(StarString other) {
        super();
        subject = other.subject;
        triples = other.triples;
    }

    public TripleString getTriple(int pos) {
        Tuple<CharSequence, CharSequence> t = triples.get(pos);
        return new TripleString(subject, t.x, t.y);
    }

    public List<CharSequence> getPredicates() {
        List<CharSequence> ret = new ArrayList<>();

        for(Tuple<CharSequence, CharSequence> t : triples) {
            ret.add(t.x);
        }

        return ret;
    }

    public int size() {
        return triples.size();
    }

    public CharSequence getSubject() {
        return subject;
    }

    public void setSubject(CharSequence subject) {
        this.subject = subject;
    }

    public List<Tuple<CharSequence, CharSequence>> getTriples() {
        return triples;
    }

    public void setTriples(List<Tuple<CharSequence, CharSequence>> triples) {
        this.triples = triples;
    }

    public void addTriple(Tuple<CharSequence, CharSequence> triple) {
        triples.add(triple);
    }

    public StarID toStarID(Dictionary dictionary) {
        int subj = dictionary.stringToId(subject, TripleComponentRole.SUBJECT);
        List<Tuple<Integer, Integer>> lst = new ArrayList<>();
        int size = size();
        for(int i = 0; i < size; i++) {
            TripleString tpl = getTriple(i);
            Tuple<Integer, Integer> t = new Tuple<>(
                    dictionary.stringToId(tpl.getPredicate(), TripleComponentRole.PREDICATE),
                    dictionary.stringToId(tpl.getObject(), TripleComponentRole.OBJECT)
            );
            lst.add(t);
        }

        return new StarID(subj, lst);
    }

    public void updateField(String name, String val) {
        String so = name.substring(0,1);
        int num = Integer.parseInt(name.substring(1));

        if(so.equals("p"))
            triples.get(num-1).x = val;
        else
            triples.get(num-1).y = val;
    }

    @Override
    public String toString() {
        String str = subject.toString();

        for(Tuple<CharSequence, CharSequence> t : triples) {
            str += "\n    " + t.x + " " + t.y;
        }

        return str;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StarString starID = (StarString) o;
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
        subject = "";
    }
}

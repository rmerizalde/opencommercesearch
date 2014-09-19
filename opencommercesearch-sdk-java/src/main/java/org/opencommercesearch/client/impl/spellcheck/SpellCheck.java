package org.opencommercesearch.client.impl.spellcheck;


/**
 * Represents search metadata like spell check data returned from the API.
 *
 * @author jmendez
 */
public class SpellCheck {

    private String collation;
    private String correctedTerms;
    private Term[] terms;

    public String getCorrectedTerms() {
        return correctedTerms;
    }

    public void setCorrectedTerms(String correctedTerms) {
        this.correctedTerms = correctedTerms;
    }

    public Term[] getTerms() {
        return terms;
    }

    public void setTerms(Term[] terms) {
        this.terms = terms;
    }

    public String getCollation() {
        return collation;
    }

    public void setCollation(String collation) {
        this.collation = collation;
    }
}

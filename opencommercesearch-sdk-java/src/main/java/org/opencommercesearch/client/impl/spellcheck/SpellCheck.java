package org.opencommercesearch.client.impl.spellcheck;


/**
 * Represents spell check data returned from the API.
 *
 * @author jmendez
 */
public class SpellCheck {

    private String collation;
    private String correctedTerms;
    private Term[] terms;
    private Boolean similarResults;

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

    /**
     * If spellCheck was set to 'auto', product responses may contain exact or similar results.
     * This method indicates if similar results are returned.
     * <p/>
     * For example, a search for "term1 term2" would usually return products that contain both
     * 'term1' and 'term2'. If this method returns true, is because the original search did not produce
     * any results and similar results were returned (if any). If set to true, products returned match 'token1' or
     * 'token2', but not necessarily both of them as opposed to exact match results.
     * @return Whether or not the results include similar results.
     */
    public Boolean getSimilarResults() {
        return similarResults;
    }

    public void setSimilarResults(Boolean similarResults) {
        this.similarResults = similarResults;
    }

    public String getCollation() {
        return collation;
    }

    public void setCollation(String collation) {
        this.collation = collation;
    }
}

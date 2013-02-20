package org.opencommercesearch.repository;

/**
 * Constants for the property names in the rule item descriptor
 * 
 * @author rmerizalde
 */
public class RuleProperty {
    protected RuleProperty() {
    }

    public static final String ID = "id";
    public static final String QUERY = "query";
    public static final String START_DATE = "startDate";
    public static final String END_DATE = "endDate";
    public static final String SITES = "sites";
    public static final String CATALOGS = "catalogs";
    public static final String CATEGORIES = "categories";
    public static final String RULE_TYPE = "ruleType";

    public static final String TYPE_BOOST_RULE = "boostRule";
    public static final String TYPE_BLOCK_RULE = "blockRule";
    public static final String TYPE_FACET_RULE = "facetRule";
    public static final String TYPE_RANKING_RULE = "rankingRule";
    public static final String TYPE_REDIRECT_RULE  = "redirectRule";
}

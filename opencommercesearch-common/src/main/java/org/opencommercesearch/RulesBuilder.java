package org.opencommercesearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.opencommercesearch.repository.CategoryProperty;
import org.opencommercesearch.repository.RankingRuleProperty;
import org.opencommercesearch.repository.RuleBasedCategoryProperty;
import org.opencommercesearch.repository.RuleExpressionProperty;

import atg.nucleus.GenericService;
import atg.repository.Repository;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;

/**
 * This class is used to generate a filter query for solr from a rule based
 * category.
 * 
 * @author gsegura
 * 
 */
public class RulesBuilder extends GenericService {

    private Repository productCatalog;

    public Repository getProductCatalog() {
        return productCatalog;
    }

    public void setProductCatalog(Repository productCatalog) {
        this.productCatalog = productCatalog;
    }

    enum Rule {
        PCT_OFF {
            public String toFilter(String ruleValue, Locale locale, Repository productCatalog) {                
                return new StringBuilder().append("discountPercent").append(locale.getCountry()).append(":")
                        .append("["+ruleValue+" TO 100]").toString();
            }
        },
        CATEGORY {
            public String toFilter(String ruleValue, Locale locale, Repository productCatalog) {                
                    try{
                        RepositoryItem category = productCatalog.getItem(ruleValue, CategoryProperty.ITEM_DESCRIPTOR);
                        if(category != null && !RuleBasedCategoryProperty.ITEM_DESCRIPTOR.equals(category.getItemDescriptor().getItemDescriptorName())){
                            
                            Set<String> searchTokens = (Set<String>) category.getPropertyValue(CategoryProperty.SEARCH_TOKENS);
                            if (searchTokens != null && searchTokens.size() > 0) {
                                String searchToken = ClientUtils.escapeQueryChars(searchTokens.iterator().next());
                                return new StringBuilder().append("category:").append(searchToken).toString();
                            } else {
                                return "";
                            }
                        }
                        else {
                            return new StringBuilder().append("categoryId:").append(ruleValue).toString();
                        }
                    } catch (RepositoryException e) {
                        throw new RuntimeException("Error loading the search tokens in the rules builder for category: "+ ruleValue, e);
                    }
            }
        },
        BRAND {
            public String toFilter(String ruleValue, Locale locale, Repository productCatalog) {
                return new StringBuilder().append("brandId:").append(ruleValue).toString();
            }
        },
        GENDER {
            public String toFilter(String ruleValue, Locale locale, Repository productCatalog) {
                return new StringBuilder().append("gender:").append(ruleValue).toString();
            }
        },
        SHOW_SALE {
            public String toFilter(String ruleValue, Locale locale, Repository productCatalog) {
                return new StringBuilder().append("onsale").append(locale.getCountry()).append(":").append(ruleValue)
                        .toString();
            }
        },
        PAST_SEASON {
            public String toFilter(String ruleValue, Locale locale, Repository productCatalog) {
                return new StringBuilder().append("isPastSeason:").append(ruleValue).toString();
            }
        },
        PRICE {
            public String toFilter(String ruleValue, Locale locale, Repository productCatalog) {
                String[] parts = ruleValue.split(" ");
                String min = parts[0];
                String max = parts[1];
                return new StringBuilder().append("salePrice").append(locale.getCountry()).append(":[").append(min+" TO "+max).append("]").toString();
            }
        },
        KEYWORD {
            public String toFilter(String ruleValue, Locale locale, Repository productCatalog) {
                return new StringBuilder().append("keyword:").append(ClientUtils.escapeQueryChars(ruleValue)).toString();
            }
        };

        public String toFilter(RepositoryItem ruleItem, Locale locale, Repository productCatalog) {
            String value = (String) ruleItem.getPropertyValue(RuleExpressionProperty.VALUE);
            
            value = value.trim();
            if (value.endsWith(",")) {
                value = value.substring(0, value.length() - 1);
            }
            
            return toFilter(value, locale, productCatalog);
        }

        public abstract String toFilter(String ruleValue, Locale locale, Repository productCatalog);
    }

    /**
     * Generates a solr filter string from a rule based category for a given
     * locale. It transforms the expressions associated to the rule based
     * category to filter statements solr can use.
     * 
     * @param categoryId
     *            The id of the rule based category
     * @param locale
     *            The locale to use
     * @return A string representation for solr of the rule based category
     */
    public String buildRulesFilter(String categoryId, Locale locale) {
        String rule = "";
        try {
            RepositoryItem category = productCatalog.getItem(categoryId, RuleBasedCategoryProperty.ITEM_DESCRIPTOR);
            if (category != null) {
                rule = buildFilter(category, getExpressions(category, RuleBasedCategoryProperty.EXPRESSIONS), locale);
            }            
        } catch (RepositoryException e) {
            if (isLoggingError()) {
                logError("error generating rules for rule category:" + categoryId, e);
            }
        }

        return rule;
    }

    public String buildRankingRuleFilter(RepositoryItem rankingRule, Locale locale) {
        return buildFilter(null, getExpressions(rankingRule, RankingRuleProperty.EXPRESSIONS), locale);
    }
    
    private List<RepositoryItem> getExpressions(RepositoryItem ruleBasedItem, String propertyName) {
        @SuppressWarnings("unchecked")
        List<RepositoryItem> expressions = (List<RepositoryItem>) ruleBasedItem
                .getPropertyValue(propertyName);
        if (expressions == null) {
            return new ArrayList<RepositoryItem>();
        }

        return expressions;
    }

    private String buildFilter(RepositoryItem category, List<RepositoryItem> ruleExpressions, Locale locale) {
        StringBuilder filter = new StringBuilder();

        if (category != null) {
            filter.append("(").append(Rule.CATEGORY.toFilter(category.getRepositoryId(), locale, productCatalog)).append(")");
        }
        
        if (ruleExpressions.size() > 0) {
            if (category != null) {
                filter.append(" OR ");
            }
            filter.append("(");
            
            int currentNestLevel = 1;
            int nesting = 0;
            boolean isFirst = true;
            boolean isClosingLevel = false;
            for (RepositoryItem ruleItem : ruleExpressions) {
    
                String type = (String) ruleItem.getPropertyValue(RuleExpressionProperty.TYPE);
                Rule rule = Rule.valueOf(type.toUpperCase());
    
                int nestLevel = (Integer) ruleItem.getPropertyValue(RuleExpressionProperty.NESTED_LEVEL);
                String operator = (String) ruleItem.getPropertyValue(RuleExpressionProperty.OPERATOR);
                String ruleString = (rule.toFilter(ruleItem, locale, productCatalog));
    
                isClosingLevel = nestLevel < currentNestLevel;
                
                if (!isFirst && !isClosingLevel) {
                    filter.append(" ").append(operator).append(" ");
                }
                
                if (nestLevel > currentNestLevel) {
                    nesting++;
                    filter.append("(");
                }
                
                if (nestLevel < currentNestLevel) {
                    nesting--;
                    filter.append(")");
                    filter.append(" ").append(operator).append(" ");
                }
    
                currentNestLevel = nestLevel;
    
                filter.append(ruleString);
    
                isFirst = false;
    
            }
    
            for (int n = 0; n < nesting; n++) {
                filter.append(")");
            }
            filter.append(")");
        }

        if (category != null) {
            List<RepositoryItem> childCategories = (List<RepositoryItem>) category.getPropertyValue(CategoryProperty.FIXED_CHILD_CATEGORIES);
            if (childCategories != null && childCategories.size() > 0){
               List<String> childFilterRule = new ArrayList<String>(childCategories.size());
               for(RepositoryItem childCategory: childCategories) {
                   childFilterRule.add("("+buildRulesFilter(childCategory.getRepositoryId(),locale)+")");
               }
               filter.append(" OR " + StringUtils.join(childFilterRule, " OR "));
             }
        }
        
        return filter.toString().trim();
    }
}

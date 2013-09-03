package org.opencommercesearch;

/*
* Licensed to OpenCommerceSearch under one
* or more contributor license agreements. See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership. OpenCommerceSearch licenses this
* file to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*/

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
                return new StringBuilder().append("ancestorCategoryId:").append(ruleValue).toString();
            }
        },
        BRAND {
            public String toFilter(String ruleValue, Locale locale, Repository productCatalog) {
                return new StringBuilder().append("brandId:").append(ruleValue).toString();
            }
        },
        GENDER {
            public String toFilter(String ruleValue, Locale locale, Repository productCatalog) {
                return new StringBuilder().append("gender:").append(ClientUtils.escapeQueryChars(ruleValue)).toString();
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
        OUTLET {
            public String toFilter(String ruleValue, Locale locale, Repository productCatalog) {
                return new StringBuilder().append("isCloseout:").append(ruleValue).toString();
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
        return buildFilter(null, getExpressions(rankingRule, RankingRuleProperty.CONDITIONS), locale);
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
                String operator = getOperator(ruleItem);
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

        return filter.toString().trim();
    }

    private String getOperator(RepositoryItem ruleItem) {
        String operator = (String) ruleItem.getPropertyValue(RuleExpressionProperty.OPERATOR);

        if ("ANDNOT".equals(operator)) {
            operator = "AND NOT";
        }
        return operator;
    }
}

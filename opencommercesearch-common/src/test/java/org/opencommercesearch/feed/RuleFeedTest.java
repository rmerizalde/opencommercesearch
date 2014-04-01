package org.opencommercesearch.feed;

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

import atg.json.JSONArray;
import atg.json.JSONException;
import atg.json.JSONObject;
import atg.repository.Repository;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryItemDescriptor;

import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opencommercesearch.RuleManager;
import org.opencommercesearch.RulesBuilder;
import org.opencommercesearch.Utils;
import org.opencommercesearch.repository.CategoryProperty;
import org.opencommercesearch.repository.RankingRuleProperty;
import org.opencommercesearch.repository.RuleProperty;

import com.google.common.collect.Lists;

import java.sql.Timestamp;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;
import static org.opencommercesearch.RulesTestUtil.mockRule;

@RunWith(MockitoJUnitRunner.class)
public class RuleFeedTest {

    @Mock
    private Repository repository;

    @Mock
    private RepositoryItem testRuleItem;

    @Mock
    private RepositoryItem siteA, siteB, siteC;

    @Mock
    private RepositoryItem cataA, cataB, cataC;

    @Mock
    private RepositoryItem cateA, cateB, cateC, cateCchild1, cateCchild2, cateCchild3;

    @Mock
    private RepositoryItem cateCchild1child1, cateCchild1child2, cateCchild1child3;

    @Mock
    private RepositoryItemDescriptor cateDescriptor, faultyDescriptor;

    private static final String EXPECTED_WILDCARD = "__all__";

    private RulesBuilder builder = new RulesBuilder();

    private RuleFeed ruleFeed = new RuleFeed();

    @Before
    public void setup() throws Exception {
        when(testRuleItem.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn("someType");
        when(testRuleItem.getPropertyValue(RuleProperty.TARGET)).thenReturn("someTarget");

        ruleFeed.setRulesBuilder(builder);
    }

    @Before
    public void setUpSitesForCreateRuleDocument() {
        when(siteA.getRepositoryId()).thenReturn("site:alpha");
        when(siteB.getRepositoryId()).thenReturn("site:beta");
        when(siteC.getRepositoryId()).thenReturn("site:charlie");
    }

    @Before
    public void setUpCatalogsForCreateRuleDocument() {
        when(cataA.getRepositoryId()).thenReturn("cata:alpha");
        Set<String> siteSet = new HashSet<String>();
        siteSet.add("site:alpha");
        when(cataA.getPropertyValue("siteIds")).thenReturn(siteSet);

        when(cataB.getRepositoryId()).thenReturn("cata:beta");
        siteSet = new HashSet<String>();
        siteSet.add("site:beta");
        when(cataB.getPropertyValue("siteIds")).thenReturn(siteSet);

        when(cataC.getRepositoryId()).thenReturn("cata:charlie");
        siteSet = new HashSet<String>();
        siteSet.add("site:charlie");
        when(cataC.getPropertyValue("siteIds")).thenReturn(siteSet);

    }

    @Before
    public void setUpCategoriesForCreateRuleDocument() throws RepositoryException {
        // cateA has 2 search tokens
        when(cateA.getRepositoryId()).thenReturn("cate:alpha");
        when(cateA.getPropertyValue(CategoryProperty.SEARCH_TOKENS)).thenReturn(new HashSet<String>(Arrays.asList(new String[]{"cateA:token1", "cateA:token2",})));
        // cateB has 1 search token
        when(cateB.getRepositoryId()).thenReturn("cate:beta");
        when(cateB.getPropertyValue(CategoryProperty.SEARCH_TOKENS)).thenReturn(new HashSet<String>(Arrays.asList(new String[]{ "cateB:token", })));
        // cateC has 0 search tokens
        when(cateC.getRepositoryId()).thenReturn("cate:charlie");
        // cateC has 2 children categories however
        when(cateC.getPropertyValue(CategoryProperty.CHILD_CATEGORIES)).thenReturn(new LinkedList<RepositoryItem>(Arrays.asList(cateCchild1, cateCchild2, cateCchild3)));

        // cateCchildx search tokens...
        when(cateCchild1.getPropertyValue(CategoryProperty.SEARCH_TOKENS)).thenReturn(new HashSet<String>(Arrays.asList(new String[]{ "cateCchild1:token", })));
        when(cateCchild2.getPropertyValue(CategoryProperty.SEARCH_TOKENS)).thenReturn(new HashSet<String>(Arrays.asList(new String[]{ "cateCchild2:token", })));
        when(cateCchild3.getPropertyValue(CategoryProperty.SEARCH_TOKENS)).thenReturn(new HashSet<String>(Arrays.asList(new String[]{ "cateCchild3:token:INVISIBLE!!!!", })));

        // cateCchild1childx search tokens...
        when(cateCchild1child1.getPropertyValue(CategoryProperty.SEARCH_TOKENS)).thenReturn(new HashSet<String>(Arrays.asList(new String[]{ "cateCchild1.cateCchild1child1:token", })));
        when(cateCchild1child2.getPropertyValue(CategoryProperty.SEARCH_TOKENS)).thenReturn(new HashSet<String>(Arrays.asList(new String[]{ "cateCchild1.cateCchild1child2:token", })));
        when(cateCchild1child3.getPropertyValue(CategoryProperty.SEARCH_TOKENS)).thenReturn(new HashSet<String>(Arrays.asList(new String[]{ "cateCchild1.cateCchild1child3:token", })));

        // set up a descriptor for all of the category search tokens
        when(cateDescriptor.getItemDescriptorName()).thenReturn("category");
        for (RepositoryItem r : new RepositoryItem[] {cateA, cateB, cateC, cateCchild1, cateCchild2, }) {
            when(r.getItemDescriptor()).thenReturn(cateDescriptor);
        }
        // make the INVISIBLE version... it won't get into the output
        when(faultyDescriptor.getItemDescriptorName()).thenReturn("notcategory");
        when(cateCchild3.getItemDescriptor()).thenReturn(faultyDescriptor);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testCateGoryPathForRuleBasedCategory() throws RepositoryException, JSONException {
        when(testRuleItem.getRepositoryId()).thenReturn("superduper");
        when(testRuleItem.getPropertyValue(RuleProperty.CATEGORIES)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{cateCchild3, })));
        when(cateCchild3.getItemDescriptor().getItemDescriptorName()).thenReturn(null);
        when(cateCchild3.getRepositoryId()).thenReturn("cateCchild3");
        when(cateCchild3.getPropertyValue(CategoryProperty.FIXED_PARENT_CATEGORIES)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{cateC, })));
        when(cateC.getRepositoryId()).thenReturn("cateC");
        when(cateC.getPropertyValue(CategoryProperty.PARENT_CATALOGS)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{cataC, cataB, })));
        when(cataC.getRepositoryId()).thenReturn("cataC");
        when(cataB.getRepositoryId()).thenReturn("cataB");

        ruleFeed = new RuleFeed();
        JSONObject doc = ruleFeed.repositoryItemToJson(testRuleItem);
        Set<String> paths = new HashSet<String>();
        paths.add("cataC.cateCchild3");
        paths.add("cataB.cateCchild3");
        List<String> calculatedPaths = (List<String>)doc.get("category");
        assertEquals(paths.toArray()[0], calculatedPaths.get(0));
        assertEquals(paths.toArray()[1], calculatedPaths.get(1));
    }

    @Test
    public void testCreateRuleDocumentNullQueryAll() throws RepositoryException, JSONException {
        // if you set NOTHING in the rule
        when(testRuleItem.getRepositoryId()).thenReturn("superduper");

        JSONObject doc = ruleFeed.repositoryItemToJson(testRuleItem);
        assertEquals("superduper", doc.get("id"));
        assertEquals(EXPECTED_WILDCARD, doc.get("query"));
        assertThat((List<String>)doc.get("siteId"), CoreMatchers.hasItem(EXPECTED_WILDCARD));
        assertThat((List<String>)doc.get("catalogId"), CoreMatchers.hasItem(EXPECTED_WILDCARD));
        assertThat((List<String>)doc.get("category"), CoreMatchers.hasItem(EXPECTED_WILDCARD));
    }

    @Test
    public void testCreateRuleDocumentWithDates() throws RepositoryException, JSONException {
        // if you set NOTHING in the rule
        when(testRuleItem.getRepositoryId()).thenReturn("superduper");
        when(testRuleItem.getPropertyValue(RuleProperty.START_DATE)).thenReturn(new Timestamp(20000));
        when(testRuleItem.getPropertyValue(RuleProperty.END_DATE)).thenReturn(new Timestamp(25000));

        JSONObject doc = ruleFeed.repositoryItemToJson(testRuleItem);
        assertEquals("superduper", doc.get("id"));
        assertEquals(EXPECTED_WILDCARD, doc.get("query"));
        assertThat((List<String>)doc.get("siteId"), CoreMatchers.hasItem(EXPECTED_WILDCARD));
        assertThat((List<String>)doc.get("catalogId"), CoreMatchers.hasItem(EXPECTED_WILDCARD));
        assertThat((List<String>)doc.get("category"), CoreMatchers.hasItem(EXPECTED_WILDCARD));
        assertEquals(Utils.getISO8601Date(20000), doc.get("startDate"));
        assertEquals(Utils.getISO8601Date(25000), doc.get("endDate"));
    }

    @Test
    public void testCreateRuleDocumentStarQueryAll() throws RepositoryException, JSONException {
        // if you use a * for the rule query & everything else is unset
        when(testRuleItem.getRepositoryId()).thenReturn("superduper");
        when(testRuleItem.getPropertyValue(RuleProperty.QUERY)).thenReturn("*");

        JSONObject doc = ruleFeed.repositoryItemToJson(testRuleItem);
        assertEquals("superduper", doc.get("id"));
        assertEquals(EXPECTED_WILDCARD, doc.get("query"));
        assertThat((List<String>)doc.get("siteId"), CoreMatchers.hasItem(EXPECTED_WILDCARD));
        assertThat((List<String>)doc.get("catalogId"), CoreMatchers.hasItem(EXPECTED_WILDCARD));
        assertThat((List<String>)doc.get("category"), CoreMatchers.hasItem(EXPECTED_WILDCARD));
    }

    @Test
    public void testCreateRuleDocumentActualQueryAll() throws RepositoryException, JSONException {
        // if you use a string for the rule query & everything else is unset
        when(testRuleItem.getRepositoryId()).thenReturn("myid");
        when(testRuleItem.getPropertyValue(RuleProperty.QUERY)).thenReturn("arc'teryx");

        JSONObject doc = ruleFeed.repositoryItemToJson(testRuleItem);
        assertEquals("myid", doc.get("id"));
        assertEquals("arc'teryx", doc.get("query"));
        assertThat((List<String>)doc.get("siteId"), CoreMatchers.hasItem(EXPECTED_WILDCARD));
        assertThat((List<String>)doc.get("catalogId"), CoreMatchers.hasItem(EXPECTED_WILDCARD));
        assertThat((List<String>)doc.get("category"), CoreMatchers.hasItem(EXPECTED_WILDCARD));
    }

    @Test
    public void testCreateRuleDocumentMulti() throws RepositoryException, JSONException {
        // if you use a string for each attribute that will be checked
        when(testRuleItem.getRepositoryId()).thenReturn("howdy_id");
        when(testRuleItem.getPropertyValue(RuleProperty.QUERY)).thenReturn("arc'teryx");
        when(testRuleItem.getPropertyValue(RuleProperty.INCLUDE_SUBCATEGORIES)).thenReturn(Boolean.TRUE);

        when(testRuleItem.getPropertyValue(RuleProperty.SITES)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{ siteA, siteB, siteC})));
        when(testRuleItem.getPropertyValue(RuleProperty.CATALOGS)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{ cataA, cataB, cataC})));
        when(testRuleItem.getPropertyValue(RuleProperty.CATEGORIES)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{ cateA, cateB, cateC })));

        JSONObject doc = ruleFeed.repositoryItemToJson(testRuleItem);
        assertEquals("howdy_id", doc.get("id"));
        assertEquals("arc'teryx", doc.get("query"));

        @SuppressWarnings("unchecked")
        JSONArray sites = (JSONArray) doc.get("siteId");
        List<String> siteList = Lists.newArrayList(sites);
        assertThat(siteList, CoreMatchers.hasItem("site:alpha"));
        assertThat(siteList, CoreMatchers.hasItem("site:beta"));
        assertThat(siteList, CoreMatchers.hasItem("site:charlie"));

        @SuppressWarnings("unchecked")
        JSONArray catalogs = (JSONArray) doc.get("catalogId");
        List<String> catalogList = Lists.newArrayList(catalogs);
        assertThat(catalogList, CoreMatchers.hasItem("cata:alpha"));
        assertThat(catalogList, CoreMatchers.hasItem("cata:beta"));
        assertThat(catalogList, CoreMatchers.hasItem("cata:charlie"));

        @SuppressWarnings("unchecked")
        JSONArray categories = (JSONArray) doc.get("category");
        List<String> categoryList = Lists.newArrayList(categories);
        for (String token : new String[] {
                "cateA:token1",
                "cateA:token2",
                "cateB:token",
                "cateCchild1:token",
                "cateCchild2:token",
        }) {
            assertThat(categoryList, CoreMatchers.hasItem(token));
        }
    }

    @Test
    public void testRankingRuleSimpleBrandRule() throws RepositoryException, JSONException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        RepositoryItem expression = mockRule("brand", 1, "88", null);

        expresionList.add(expression);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.CONDITIONS)).thenReturn(expresionList);
        when(testRuleItem.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn("rankingRule");
        when(testRuleItem.getPropertyValue(RankingRuleProperty.BOOST_BY)).thenReturn(RankingRuleProperty.BOOST_BY_FACTOR);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.STRENGTH)).thenReturn(RankingRuleProperty.STRENGTH_MAXIMUM_BOOST);

        JSONObject doc = ruleFeed.repositoryItemToJson(testRuleItem);
        assertEquals("if(exists(query({!lucene v='(brandId:88)'})),10.0,1.0)", doc.get(RuleManager.FIELD_BOOST_FUNCTION));
    }

    @Test
    public void testRankingRuleSimplePercentageRule() throws RepositoryException, JSONException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        RepositoryItem expression = mockRule("pct_off", 1, "15", null);

        expresionList.add(expression);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.CONDITIONS)).thenReturn(expresionList);
        when(testRuleItem.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn("rankingRule");
        when(testRuleItem.getPropertyValue(RankingRuleProperty.BOOST_BY)).thenReturn(RankingRuleProperty.BOOST_BY_FACTOR);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.STRENGTH)).thenReturn(RankingRuleProperty.STRENGTH_MAXIMUM_DEMOTE);

        JSONObject doc = ruleFeed.repositoryItemToJson(testRuleItem);
        assertEquals("if(exists(query({!lucene v='(discountPercentUS:[15 TO 100])'})),0.1,1.0)", doc.get(RuleManager.FIELD_BOOST_FUNCTION));
    }

    @Test
    public void testRankingRuleSimpleGenderRule() throws RepositoryException, JSONException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        RepositoryItem expression = mockRule("gender", 1, "Boys'", null);

        expresionList.add(expression);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.CONDITIONS)).thenReturn(expresionList);
        when(testRuleItem.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn("rankingRule");
        when(testRuleItem.getPropertyValue(RankingRuleProperty.BOOST_BY)).thenReturn(RankingRuleProperty.BOOST_BY_ATTRIBUTE_VALUE);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.ATTRIBUTE)).thenReturn("listRank");

        JSONObject doc = ruleFeed.repositoryItemToJson(testRuleItem);
        assertEquals("if(exists(query({!lucene v='(gender:Boys\\')'})),listRank,1.0)", doc.get(RuleManager.FIELD_BOOST_FUNCTION));
    }

    @Test
    public void testRankingRuleSimpleShowSaleRule() throws RepositoryException, JSONException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        RepositoryItem expression = mockRule("show_sale", 1, "false", null);

        expresionList.add(expression);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.CONDITIONS)).thenReturn(expresionList);
        when(testRuleItem.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn("rankingRule");
        when(testRuleItem.getPropertyValue(RankingRuleProperty.BOOST_BY)).thenReturn(RankingRuleProperty.BOOST_BY_FACTOR);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.STRENGTH)).thenReturn(RankingRuleProperty.STRENGTH_MEDIUM_DEMOTE);

        JSONObject doc = ruleFeed.repositoryItemToJson(testRuleItem);
        assertEquals("if(exists(query({!lucene v='(onsaleUS:false)'})),0.5,1.0)", doc.get(RuleManager.FIELD_BOOST_FUNCTION));
    }

    @Test
    public void testRankingRuleSimplePastSeasonRule() throws RepositoryException, JSONException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        RepositoryItem expression = mockRule("past_season", 1, "false", null);

        expresionList.add(expression);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.CONDITIONS)).thenReturn(expresionList);
        when(testRuleItem.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn("rankingRule");
        when(testRuleItem.getPropertyValue(RankingRuleProperty.BOOST_BY)).thenReturn(RankingRuleProperty.BOOST_BY_FACTOR);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.STRENGTH)).thenReturn(RankingRuleProperty.STRENGTH_MEDIUM_BOOST);

        JSONObject doc = ruleFeed.repositoryItemToJson(testRuleItem);
        assertEquals("if(exists(query({!lucene v='(isPastSeason:false)'})),2.0,1.0)", doc.get(RuleManager.FIELD_BOOST_FUNCTION));
    }

    @Test
    public void testRankingRuleSimpleBrandPastSeasonRule() throws RepositoryException, JSONException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("past_season", 1, "false", null));
        expresionList.add(mockRule("brand", 1, "88", "AND"));

        when(testRuleItem.getPropertyValue(RankingRuleProperty.CONDITIONS)).thenReturn(expresionList);
        when(testRuleItem.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn("rankingRule");
        when(testRuleItem.getPropertyValue(RankingRuleProperty.BOOST_BY)).thenReturn(RankingRuleProperty.BOOST_BY_FACTOR);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.STRENGTH)).thenReturn(RankingRuleProperty.STRENGTH_MEDIUM_BOOST);

        JSONObject doc = ruleFeed.repositoryItemToJson(testRuleItem);
        assertEquals("if(exists(query({!lucene v='(isPastSeason:false AND brandId:88)'})),2.0,1.0)", doc.get(RuleManager.FIELD_BOOST_FUNCTION));
    }

    @Test
    public void testRankingRuleSimpleKeywordRule() throws RepositoryException, JSONException {

        String keyword = "this is a big weird keyword'";
        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        RepositoryItem expression = mockRule("keyword", 1, keyword, null);

        expresionList.add(expression);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.CONDITIONS)).thenReturn(expresionList);
        when(testRuleItem.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn("rankingRule");
        when(testRuleItem.getPropertyValue(RankingRuleProperty.BOOST_BY)).thenReturn(RankingRuleProperty.BOOST_BY_FACTOR);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.STRENGTH)).thenReturn(RankingRuleProperty.STRENGTH_WEAK_BOOST);

        JSONObject doc = ruleFeed.repositoryItemToJson(testRuleItem);
        assertEquals("if(exists(query({!lucene v='(keyword:this\\ is\\ a\\ big\\ weird\\ keyword\\')'})),1.5,1.0)",
               doc.get(RuleManager.FIELD_BOOST_FUNCTION));
    }

    @Test
    public void testRankingRuleNoConditions() throws RepositoryException, JSONException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();

        when(testRuleItem.getPropertyValue(RankingRuleProperty.CONDITIONS)).thenReturn(expresionList);
        when(testRuleItem.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn("rankingRule");
        when(testRuleItem.getPropertyValue(RankingRuleProperty.BOOST_BY)).thenReturn(RankingRuleProperty.BOOST_BY_ATTRIBUTE_VALUE);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.ATTRIBUTE)).thenReturn("div(1,listRank)");

        JSONObject doc = ruleFeed.repositoryItemToJson(testRuleItem);
        assertEquals("div(1,listRank)", doc.get(RuleManager.FIELD_BOOST_FUNCTION));
    }

    @Test
    public void testRankingRuleComplexRule() throws RepositoryException, JSONException {
        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("brand", 1, "88", null));
        expresionList.add(mockRule("brand", 2, "77", "OR"));
        expresionList.add(mockRule("show_sale", 2, "true", "AND"));
        expresionList.add(mockRule("past_season", 3, "false", "AND"));

        when(testRuleItem.getPropertyValue(RankingRuleProperty.CONDITIONS)).thenReturn(expresionList);
        when(testRuleItem.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn("rankingRule");
        when(testRuleItem.getPropertyValue(RankingRuleProperty.BOOST_BY)).thenReturn(RankingRuleProperty.BOOST_BY_FACTOR);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.STRENGTH)).thenReturn(RankingRuleProperty.STRENGTH_WEAK_BOOST);

        JSONObject doc = ruleFeed.repositoryItemToJson(testRuleItem);
        assertEquals("if(exists(query({!lucene v='(brandId:88 OR (brandId:77 AND onsaleUS:true AND (isPastSeason:false)))'})),1.5,1.0)",
                doc.get(RuleManager.FIELD_BOOST_FUNCTION));
    }

    @Test
    public void testCateGoryPathRegularCategoryWithSubCategories() throws RepositoryException, JSONException {
        when(testRuleItem.getRepositoryId()).thenReturn("superduper");
        when(testRuleItem.getPropertyValue(RuleProperty.INCLUDE_SUBCATEGORIES)).thenReturn(true);
        when(cateCchild1child1.getRepositoryId()).thenReturn("cateCchild1child1");
        when(cateCchild1child2.getRepositoryId()).thenReturn("cateCchild1child2");
        when(cateCchild1child3.getRepositoryId()).thenReturn("cateCchild1child3");
        when(cateCchild1child1.getPropertyValue(CategoryProperty.FIXED_PARENT_CATEGORIES)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{cateCchild1, })));
        when(cateCchild1child2.getPropertyValue(CategoryProperty.FIXED_PARENT_CATEGORIES)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{cateCchild1, })));
        when(cateCchild1child3.getPropertyValue(CategoryProperty.FIXED_PARENT_CATEGORIES)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{cateCchild1, })));

        when(testRuleItem.getPropertyValue(RuleProperty.CATEGORIES)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{cateCchild1, })));
        when(cateCchild1.getItemDescriptor().getItemDescriptorName()).thenReturn(CategoryProperty.ITEM_DESCRIPTOR);
        when(cateCchild1.getRepositoryId()).thenReturn("cateCchild1");
        when(cateCchild1.getPropertyValue(CategoryProperty.FIXED_PARENT_CATEGORIES)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{cateC, })));
        when(cateCchild1.getPropertyValue(CategoryProperty.CHILD_CATEGORIES)).thenReturn(new ArrayList<RepositoryItem>(Arrays.asList(new RepositoryItem[]{cateCchild1child1,cateCchild1child2,cateCchild1child3, })));

        when(cateC.getRepositoryId()).thenReturn("cateC");
        when(cateC.getPropertyValue(CategoryProperty.PARENT_CATALOGS)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{cataC, cataB, })));
        when(cataC.getRepositoryId()).thenReturn("cataC");
        when(cataB.getRepositoryId()).thenReturn("cataB");

        JSONObject doc = ruleFeed.repositoryItemToJson(testRuleItem);
        List<String> calculatedPaths = (List<String>)doc.get("category");
        assertEquals("cateCchild1:token", calculatedPaths.get(0));
        assertEquals("cateCchild1.cateCchild1child1:token", calculatedPaths.get(1));
        assertEquals("cateCchild1.cateCchild1child2:token", calculatedPaths.get(2));
        assertEquals("cateCchild1.cateCchild1child3:token", calculatedPaths.get(3));
        assertEquals("cataC.cateCchild1", calculatedPaths.get(4));
        assertEquals("cataB.cateCchild1", calculatedPaths.get(5));
        assertEquals("cataC.cateCchild1.cateCchild1child1", calculatedPaths.get(6));
        assertEquals("cataB.cateCchild1.cateCchild1child1", calculatedPaths.get(7));
        assertEquals("cataC.cateCchild1.cateCchild1child2", calculatedPaths.get(8));
        assertEquals("cataB.cateCchild1.cateCchild1child2", calculatedPaths.get(9));
        assertEquals("cataC.cateCchild1.cateCchild1child3", calculatedPaths.get(10));
        assertEquals("cataB.cateCchild1.cateCchild1child3", calculatedPaths.get(11));
    }

    @Test
    public void testCateGoryPathRegularCategoryWithOutSubCategories() throws RepositoryException, JSONException {
        when(testRuleItem.getRepositoryId()).thenReturn("superduper");
        when(testRuleItem.getPropertyValue(RuleProperty.CATEGORIES)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{cateCchild1, })));
        when(cateCchild1.getItemDescriptor().getItemDescriptorName()).thenReturn(CategoryProperty.ITEM_DESCRIPTOR);
        when(cateCchild1.getRepositoryId()).thenReturn("cateCchild1");
        when(cateCchild1.getPropertyValue(CategoryProperty.FIXED_PARENT_CATEGORIES)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{cateC, })));
        when(cateC.getRepositoryId()).thenReturn("cateC");
        when(cateC.getPropertyValue(CategoryProperty.PARENT_CATALOGS)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{cataC, cataB, })));
        when(cataC.getRepositoryId()).thenReturn("cataC");
        when(cataB.getRepositoryId()).thenReturn("cataB");

        JSONObject doc = ruleFeed.repositoryItemToJson(testRuleItem);
        List<String> calculatedPaths = (List<String>)doc.get("category");
        assertEquals("cateCchild1:token", calculatedPaths.get(0));
        assertEquals("cataC.cateCchild1", calculatedPaths.get(1));
        assertEquals("cataB.cateCchild1", calculatedPaths.get(2));
    }
}

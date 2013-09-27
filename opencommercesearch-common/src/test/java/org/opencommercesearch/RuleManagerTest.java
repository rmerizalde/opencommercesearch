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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.matchers.JUnitMatchers.hasItem;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.opencommercesearch.RulesTestUtil.mockRule;

import java.sql.Timestamp;
import java.util.*;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.SolrParams;
import org.opencommercesearch.repository.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import atg.repository.Repository;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryItemDescriptor;

@RunWith(MockitoJUnitRunner.class)
public class RuleManagerTest {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }
    
    private static final String facetRule = "facetRule", blockRule = "blockRule", boostRule = "boostRule";
    
    @Mock private Repository repository;    
    @Mock private SolrServer server;
    @Mock private QueryResponse queryResponse;
    
    @Mock private RepositoryItem shoeItem;    
    @Mock private RepositoryItem fruitItem;    
    @Mock private RepositoryItem bottleItem;
    @Mock private RepositoryItem bagItem;
    @Mock private RepositoryItem testRuleItem;
    
    @Mock private RepositoryItem siteA, siteB, siteC;
    @Mock private RepositoryItem cataA, cataB, cataC;
    @Mock private RepositoryItem cateA, cateB, cateC, cateCchild1, cateCchild2, cateCchild3;
    @Mock private RepositoryItem cateCchild1child1, cateCchild1child2, cateCchild1child3;
    
    @Mock private RepositoryItem cateAToken1, cateAToken2;
    @Mock private RepositoryItem cateBToken;
    @Mock private RepositoryItem cateCchild1Token, cateCchild2Token;
    
    @Mock private RepositoryItemDescriptor cateDescriptor, faultyDescriptor;
    
    private static final String EXPECTED_WILDCARD = "__all__";
    
    private RulesBuilder builder = new RulesBuilder();

    @Before
    public void setup() throws Exception {
        when(testRuleItem.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn("someType");
        when(testRuleItem.getPropertyValue(RuleProperty.TARGET)).thenReturn("someTarget");
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
        when(cateA.getPropertyValue(CategoryProperty.SEARCH_TOKENS)).thenReturn(new HashSet<String>(Arrays.asList(new String[]{ "cateA:token1", "cateA:token2", })));
        // cateB has 1 search token
        when(cateB.getRepositoryId()).thenReturn("cate:beta");
        when(cateB.getPropertyValue(CategoryProperty.SEARCH_TOKENS)).thenReturn(new HashSet<String>(Arrays.asList(new String[]{ "cateB:token", })));
        // cateC has 0 search tokens
        when(cateC.getRepositoryId()).thenReturn("cate:charlie");
        // cateC has 2 children categories however
        when(cateC.getPropertyValue(CategoryProperty.CHILD_CATEGORIES)).thenReturn(new LinkedList<RepositoryItem>(Arrays.asList(new RepositoryItem[]{ cateCchild1, cateCchild2, cateCchild3, })));
        
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
    
    private void setUpRuleData(List<String> categories, String description, String id, String ruleType, RepositoryItem item, SolrDocumentList documents) throws RepositoryException {
        SolrDocument rule = new SolrDocument();
        rule.addField("description", description);
        rule.addField("id", id);
        rule.addField("category", categories);
        documents.add(rule);        
        when(item.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn(ruleType);
        when(repository.getItem(id, SearchRepositoryItemDescriptor.RULE)).thenReturn(item);
    }
    
    @Test 
    public void testSetRuleParamsAndSetFilterQueries() throws RepositoryException, SolrServerException {
        // make sure that the facetManager gets addFacet called when we supply facets
        final FacetManager facetManager = mock(FacetManager.class);        
        RuleManager mgr = new RuleManager(repository, builder, server) {
            @Override
            public FacetManager getFacetManager() {
                return facetManager;
            }
        };
                
        // we need to make sure that we test filterQueries here...
        SolrDocumentList solrDocumentList = new SolrDocumentList();
        // ---------- set up docs with a rule type -----------
        setUpRuleData(null, "i wear shoes", "supra", facetRule, shoeItem, solrDocumentList);
        // note that we do NOT add this into the Repository so that we have a null rule in loadRules, this causes this document to not go into the rules
        SolrDocument rule = new SolrDocument();
        rule.addField("description", "avacado's grow on trees!");
        rule.addField("id", "avacado");
        solrDocumentList.add(rule);    
        setUpRuleData(null, "water bottle's are nice for drinking water!", "nalgene", boostRule, bottleItem, solrDocumentList);
                       
        // ----------- set up doclist attributes ----------
        solrDocumentList.setNumFound(solrDocumentList.size()); 
        solrDocumentList.setStart(0L);
//        solrDocumentList.setMaxScore(1000.0);
        when(queryResponse.getResults()).thenReturn(solrDocumentList);
        when(server.query(any(SolrParams.class))).thenReturn(queryResponse);
        
        // ------------ make the call to load the rules etc -------------
        RepositoryItem catalog = mock(RepositoryItem.class);
        when(catalog.getRepositoryId()).thenReturn("bobcatalog");
        SolrQuery query = mock(SolrQuery.class);
        when(query.getQuery()).thenReturn("jackets");
        
        FilterQuery[] filterQueries = new FilterQuery[] {
            new FilterQuery("category", "jackets"), // is a multi
            new FilterQuery("category", "12.jackets"), // is a multi
            new FilterQuery("hasPinStripes", "redstripes"), 
            new FilterQuery("hasFeathers", "socks&stuff"), 
            new FilterQuery("hasLaces", "raingear"), // is a multi
            new FilterQuery("chopsticks", "lookout below")
        };
        
        // set up the facet items to catch all conditions
        RepositoryItem categoryFacetItem = mock(RepositoryItem.class);
        when(facetManager.getFacetItem("category")).thenReturn(categoryFacetItem);
        when(categoryFacetItem.getPropertyValue((FacetProperty.IS_MULTI_SELECT))).thenReturn(true);
        
        RepositoryItem hasPinStripesFacetItem = mock(RepositoryItem.class);
        when(facetManager.getFacetItem("hasPinStripes")).thenReturn(hasPinStripesFacetItem);
        when(hasPinStripesFacetItem.getPropertyValue((FacetProperty.IS_MULTI_SELECT))).thenReturn(false);
        
        RepositoryItem hasFeathersFacetItem = mock(RepositoryItem.class);
        when(facetManager.getFacetItem("hasFeathers")).thenReturn(hasFeathersFacetItem);
        // don't support multi for hasFeathers...
        
        RepositoryItem hasLacesFacetItem = mock(RepositoryItem.class);
        when(facetManager.getFacetItem("hasLaces")).thenReturn(hasLacesFacetItem);
        when(hasLacesFacetItem.getPropertyValue((FacetProperty.IS_MULTI_SELECT))).thenReturn(true);        
        
        // and nothing for chopsticks
        mgr.setRuleParams(query, true, false, null, filterQueries, catalog);
        
        verify(query).setFacetPrefix("category", "1.bobcatalog.");
        verify(query).addFilterQuery("category:0.bobcatalog");
        verify(query).getQuery();
        verify(query, times(2)).getSortFields();
        verify(query).setSortField("isToos", ORDER.asc);
        verify(query).addSortField("score", ORDER.desc);        
        verify(query).setFacetPrefix("category", "13.jackets.");

        // verify the single calls to addFilterQuery
        verify(query).addFilterQuery("hasPinStripes:redstripes"); // this will have a facet
        verify(query).addFilterQuery("hasFeathers:socks&stuff"); // this will have a facet, but not MULTI
        verify(query).addFilterQuery("chopsticks:lookout below"); // no facet for this one (test null path)
        
        // now verify the multi calls to addFilterQuery
        verify(query).addFilterQuery("{!tag=category}category:jackets OR category:12.jackets");
        verify(query).addFilterQuery("{!tag=hasLaces}hasLaces:raingear");        
        
        verifyNoMoreInteractions(query);        
    }
    
    @Test 
    public void testSetRuleParams2NullRules() throws RepositoryException, SolrServerException { 
        // test handling null filterQueries
        SolrDocumentList solrDocumentList = new SolrDocumentList();
        // ---------- set up docs with a rule type -----------
        setUpRuleData(null, "i wear shoes", "supra", facetRule, shoeItem, solrDocumentList);
        // note that we do NOT add this into the Repository so that we have a null rule in loadRules, this causes this document to not go into the rules
        SolrDocument rule = new SolrDocument();
        rule.addField("description", "avacado's grow on trees!");
        rule.addField("id", "avacado");
        solrDocumentList.add(rule);    
        setUpRuleData(null, "water bottle's are nice for drinking water!", "nalgene", boostRule, bottleItem, solrDocumentList);
                       
        // ----------- set up doclist attributes ----------
        solrDocumentList.setNumFound(solrDocumentList.size()); 
        solrDocumentList.setStart(0L);
//        solrDocumentList.setMaxScore(1000.0);
        when(queryResponse.getResults()).thenReturn(solrDocumentList);
        when(server.query(any(SolrParams.class))).thenReturn(queryResponse);
        
        // ----------- set up rule manager -------------
        
        RuleManager mgr = new RuleManager(repository, builder, server);
        assertEquals(null, mgr.getRules());
        
        // ------------ make the call to load the rules etc -------------
        RepositoryItem catalog = mock(RepositoryItem.class);
        when(catalog.getRepositoryId()).thenReturn("bobcatalog");
        SolrQuery query = mock(SolrQuery.class);
        when(query.getQuery()).thenReturn("jackets");
        
        mgr.setRuleParams(query, true, false, null, null, catalog);
        verify(query).setFacetPrefix("category", "1.bobcatalog.");
        verify(query).addFilterQuery("category:0.bobcatalog");
        verify(query).getQuery();
        verify(query, times(2)).getSortFields();
        verify(query).setSortField("isToos", ORDER.asc);
        verify(query).addSortField("score", ORDER.desc);
        verifyNoMoreInteractions(query);
    }
    
    @Test
    public void testSetRuleParamsBlocks() {
        RuleManager mgr = new RuleManager(repository, builder, server);
        SolrQuery query = mock(SolrQuery.class);
        Map<String, List<RepositoryItem>> typeToRules = new HashMap<String, List<RepositoryItem>>();
        
        List<RepositoryItem> rules = new ArrayList<RepositoryItem>();
        typeToRules.put(blockRule, rules);
        RepositoryItem rule = mock(RepositoryItem.class);
        rules.add(rule);
        Set<RepositoryItem> blockedProducts = new HashSet<RepositoryItem>();        
        when(rule.getPropertyValue(BlockRuleProperty.BLOCKED_PRODUCTS)).thenReturn(blockedProducts);
        
        RepositoryItem blockedProduct1 = mock(RepositoryItem.class);
        blockedProducts.add(blockedProduct1);
        when(blockedProduct1.getRepositoryId()).thenReturn("walking_carpet");
        
        RepositoryItem blockedProduct2 = mock(RepositoryItem.class);
        blockedProducts.add(blockedProduct2);
        when(blockedProduct2.getRepositoryId()).thenReturn("your_highnesness");
        
        mgr.setRuleParams(query, typeToRules);
        verify(query).setSortField("isToos", ORDER.asc);
        verify(query).addFilterQuery("-productId:walking_carpet");
        verify(query).addFilterQuery("-productId:your_highnesness");
        verify(query).addSortField("score", ORDER.desc);        
    }  
    
    @Test
    public void testSetRuleParamsBoosts() {
        RuleManager mgr = new RuleManager(repository, builder, server);
        SolrQuery query = mock(SolrQuery.class);
        Map<String, List<RepositoryItem>> typeToRules = new HashMap<String, List<RepositoryItem>>();

        List<RepositoryItem> rules = new ArrayList<RepositoryItem>();
        typeToRules.put(boostRule, rules);
        RepositoryItem rule = mock(RepositoryItem.class);
        rules.add(rule);
        List<RepositoryItem> boostedProducts = new ArrayList<RepositoryItem>();        
        when(rule.getPropertyValue(BoostRuleProperty.BOOSTED_PRODUCTS)).thenReturn(boostedProducts);
        
        RepositoryItem boostedProduct1 = mock(RepositoryItem.class);
        boostedProducts.add(boostedProduct1);
        when(boostedProduct1.getRepositoryId()).thenReturn("hello world");
        
        RepositoryItem boostedProduct2 = mock(RepositoryItem.class);
        boostedProducts.add(boostedProduct2);
        when(boostedProduct2.getRepositoryId()).thenReturn("i like food");
        
        mgr.setRuleParams(query, typeToRules);
        verify(query).setSortField("isToos", ORDER.asc);
        verify(query).addSortField("fixedBoost(productId,'hello world','i like food')", ORDER.asc);
        verify(query).addSortField("score", ORDER.desc);        
    }  
    
    
    @Test
    public void testSetRuleParamsFacets() {
        // make sure that the facetManager gets addFacet called when we supply facets
        final FacetManager facetManager = mock(FacetManager.class);        
        RuleManager mgr = new RuleManager(repository, builder, server) {
            @Override
            public FacetManager getFacetManager() {
                return facetManager;
            }
        };
        SolrQuery query = mock(SolrQuery.class);
        Map<String, List<RepositoryItem>> typeToRules = new HashMap<String, List<RepositoryItem>>();
        List<RepositoryItem> rules = new ArrayList<RepositoryItem>();
        typeToRules.put(facetRule, rules);
        RepositoryItem rule = mock(RepositoryItem.class);
        rules.add(rule);
        List<RepositoryItem> facets = new ArrayList<RepositoryItem>();
        when(rule.getRepositoryId()).thenReturn("facetRule1");
        when(rule.getPropertyValue(FacetRuleProperty.FACETS)).thenReturn(facets);
                
        RepositoryItem facet1 = mock(RepositoryItem.class);
        facets.add(facet1);
        when(facet1.getRepositoryId()).thenReturn("facet1");
        
        RepositoryItem facet2 = mock(RepositoryItem.class);
        facets.add(facet2);
        when(facet2.getRepositoryId()).thenReturn("facet2");

        Map<String, SolrDocument> ruleDocs = new HashMap<String, SolrDocument>();
        SolrDocument facetRuleDoc = mock(SolrDocument.class);
        when(facetRuleDoc.getFieldValue(FacetRuleProperty.COMBINE_MODE)).thenReturn(FacetRuleProperty.COMBINE_MODE_REPLACE);
        ruleDocs.put("facetRule1", facetRuleDoc);

        
        mgr.setRuleParams(query, typeToRules, ruleDocs);
        verify(facetManager).addFacet(facet1);
        verify(facetManager).addFacet(facet2);
        verify(facetManager).setParams(query);
        verify(facetManager).clear();
        verifyNoMoreInteractions(facetManager);     
    }
    
    // TODO: make a good test for testSetRuleParamsFacets
    
    @Test
    public void testSetRuleParamsTypesHaveNoContent() {
        RuleManager mgr = new RuleManager(repository, builder, server);
        SolrQuery query = mock(SolrQuery.class);
        Map<String, List<RepositoryItem>> typeToRules = new HashMap<String, List<RepositoryItem>>();

        List<RepositoryItem> facetRules = new ArrayList<RepositoryItem>();
        typeToRules.put(facetRule, facetRules);
        RepositoryItem frule1 = mock(RepositoryItem.class);
        when(frule1.getRepositoryId()).thenReturn("facetRule1");
        facetRules.add(frule1);

        Map<String, SolrDocument> ruleDocs = new HashMap<String, SolrDocument>();
        SolrDocument facetRuleDoc = mock(SolrDocument.class);
        when(facetRuleDoc.getFieldValue(FacetRuleProperty.COMBINE_MODE)).thenReturn(FacetRuleProperty.COMBINE_MODE_REPLACE);
        ruleDocs.put("facetRule1", facetRuleDoc);

        List<RepositoryItem> boostRules = new ArrayList<RepositoryItem>();
        typeToRules.put(boostRule, boostRules);
        RepositoryItem borule1 = mock(RepositoryItem.class);
        boostRules.add(borule1);
        

        List<RepositoryItem> blockRules = new ArrayList<RepositoryItem>();
        typeToRules.put(blockRule, blockRules);
        RepositoryItem blrule1 = mock(RepositoryItem.class);
        blockRules.add(blrule1);
                
        // this should NOT throw if no FACETS, BOOSTED_PRODUCTS or BLOCKED_PRODUCTS are found
        try {
            mgr.setRuleParams(query, typeToRules, ruleDocs);
        } catch (NullPointerException ex) {
            fail("Should protect against no rules set");
        }
    }

    @Test
    public void testSetRuleParamsWithSort() {
        RuleManager mgr = new RuleManager(repository, builder, server);
        SolrQuery query = mock(SolrQuery.class);
        when(query.getSortFields()).thenReturn(new String[] {"reviewAverage desc", "reviews asc", "reviews desc", "score asc"});
        mgr.setRuleParams(query, new HashMap());
        verify(query).getSortFields();
        verify(query).setSortField("isToos", ORDER.asc);
        verify(query).addSortField("reviewAverage", ORDER.desc);
        verify(query).addSortField("reviews", ORDER.asc);
        verify(query).addSortField("score", ORDER.desc);
        verifyNoMoreInteractions(query);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testCateGoryPathForRuleBasedCategory() throws RepositoryException {
        RuleManager mgr = new RuleManager(repository, builder, server);
        when(testRuleItem.getRepositoryId()).thenReturn("superduper");
        when(testRuleItem.getPropertyValue(RuleProperty.CATEGORIES)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{cateCchild3, })));
        when(cateCchild3.getItemDescriptor().getItemDescriptorName()).thenReturn(null);
        when(cateCchild3.getRepositoryId()).thenReturn("cateCchild3");        
        when(cateCchild3.getPropertyValue(CategoryProperty.FIXED_PARENT_CATEGORIES)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{cateC, })));
        when(cateC.getRepositoryId()).thenReturn("cateC");
        when(cateC.getPropertyValue(CategoryProperty.PARENT_CATALOGS)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{cataC, cataB, })));
        when(cataC.getRepositoryId()).thenReturn("cataC");
        when(cataB.getRepositoryId()).thenReturn("cataB");
        SolrInputDocument doc = mgr.createRuleDocument(testRuleItem);
        Set<String> paths = new HashSet<String>();
        paths.add("cataC.cateCchild3");
        paths.add("cataB.cateCchild3");
        List<String> calculatedPaths = (List<String>)doc.getField("category").getValue();
        assertEquals(paths.toArray()[0], calculatedPaths.get(0));
        assertEquals(paths.toArray()[1], calculatedPaths.get(1));
    }
    
    @Test
    public void testRankingRuleForRuleBasedCategories() throws RepositoryException, SolrServerException {
        RuleManager mgr = new RuleManager(repository, builder, server);
        SolrDocumentList ruleList = new SolrDocumentList();
        setUpRuleData(null, "i wear shoes", "supra", facetRule, shoeItem, ruleList);
        SolrDocument rule = new SolrDocument();
        rule.addField("description", "avacado's grow on trees!");
        rule.addField("id", "avacado");
        ruleList.add(rule);    
        setUpRuleData(null, "water bottle's are nice for drinking water!", "nalgene", boostRule, bottleItem, ruleList);
        List<String> categories = new ArrayList<String>();
        categories.add("myCatalog.ruleBasedCategory");
        setUpRuleData(categories, "bag are nice for carring goods !", "reebok", boostRule, bagItem, ruleList);
        ruleList.setNumFound(ruleList.size()); 
        ruleList.setStart(0L);
        when(queryResponse.getResults()).thenReturn(ruleList);
        when(server.query(any(SolrParams.class))).thenReturn(queryResponse);
        mgr.loadRules("", "myCatalog.ruleBasedCategory", null, false, true, cataA);
        assertEquals(mgr.getRules().size(), 2);
    }
    
    @Test
    public void testSetRuleParamsWithSortAndBoostRule() {
        Map<String, List<RepositoryItem>> typeToRules = new HashMap<String, List<RepositoryItem>>();
        List<RepositoryItem> rules = new ArrayList<RepositoryItem>();
        typeToRules.put(boostRule, rules);
        RepositoryItem rule = mock(RepositoryItem.class);
        rules.add(rule);
        List<RepositoryItem> boostedProducts = new ArrayList<RepositoryItem>();
        when(rule.getPropertyValue(BoostRuleProperty.BOOSTED_PRODUCTS)).thenReturn(boostedProducts);

        RepositoryItem boostedProduct1 = mock(RepositoryItem.class);
        boostedProducts.add(boostedProduct1);
        when(boostedProduct1.getRepositoryId()).thenReturn("hello world");

        RepositoryItem boostedProduct2 = mock(RepositoryItem.class);
        boostedProducts.add(boostedProduct2);
        when(boostedProduct2.getRepositoryId()).thenReturn("i like food");

        RuleManager mgr = new RuleManager(repository, builder, server);
        SolrQuery query = mock(SolrQuery.class);
        when(query.getSortFields()).thenReturn(new String[] {"reviewAverage desc", "reviews asc", "reviews desc", "score asc"});
        mgr.setRuleParams(query, typeToRules);
        verify(query, times(2)).getSortFields();
        verify(query).setSortField("isToos", ORDER.asc);
        verify(query).addSortField("reviewAverage", ORDER.desc);
        verify(query).addSortField("reviews", ORDER.asc);
        verify(query).addSortField("score", ORDER.desc);
        verifyNoMoreInteractions(query);
    }
    
    @Test
    public void testSetRuleParamsEmptyRules() {
        RuleManager mgr = new RuleManager(repository, builder, server);
        SolrQuery query = mock(SolrQuery.class);
        mgr.setRuleParams(query, new HashMap());
        verify(query).getSortFields();
        verify(query).setSortField("isToos", ORDER.asc);
        verify(query).addSortField("score", ORDER.desc);
        verifyNoMoreInteractions(query);
    }    

    @Test
    public void testSetRuleParamsNullRules() {
        RuleManager mgr = new RuleManager(repository, builder, server);
        SolrQuery query = mock(SolrQuery.class);
        mgr.setRuleParams(query, null);
        verifyNoMoreInteractions(query);
    }
    
    @Test
    public void testCreateRuleDocumentNullQueryAll() throws RepositoryException {
        // if you set NOTHING in the rule
        RuleManager mgr = new RuleManager(repository, builder, server);
        when(testRuleItem.getRepositoryId()).thenReturn("superduper");
        SolrInputDocument doc = mgr.createRuleDocument(testRuleItem);
        assertEquals("superduper", doc.getField("id").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("query").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("siteId").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("catalogId").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("category").getValue());
    }
    
    @Test
    public void testCreateRuleDocumentWithDates() throws RepositoryException {
        // if you set NOTHING in the rule
        RuleManager mgr = new RuleManager(repository, builder, server);
        when(testRuleItem.getRepositoryId()).thenReturn("superduper");
        when(testRuleItem.getPropertyValue(RuleProperty.START_DATE)).thenReturn(new Timestamp(20000));
        when(testRuleItem.getPropertyValue(RuleProperty.END_DATE)).thenReturn(new Timestamp(25000));
        SolrInputDocument doc = mgr.createRuleDocument(testRuleItem);
        assertEquals("superduper", doc.getField("id").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("query").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("siteId").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("catalogId").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("category").getValue());
        assertEquals(new Timestamp(20000), doc.getField("startDate").getValue());
        assertEquals(new Timestamp(25000), doc.getField("endDate").getValue());
    }
    
    @Test
    public void testCreateRuleDocumentStarQueryAll() throws RepositoryException {
        // if you use a * for the rule query & everything else is unset
        RuleManager mgr = new RuleManager(repository, builder, server);
        when(testRuleItem.getRepositoryId()).thenReturn("superduper");
        when(testRuleItem.getPropertyValue(RuleProperty.QUERY)).thenReturn("*");
        SolrInputDocument doc = mgr.createRuleDocument(testRuleItem);
        assertEquals("superduper", doc.getField("id").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("query").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("siteId").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("catalogId").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("category").getValue());        
    }
    
    @Test
    public void testCreateRuleDocumentActualQueryAll() throws RepositoryException {
        // if you use a string for the rule query & everything else is unset
        RuleManager mgr = new RuleManager(repository, builder, server);
        when(testRuleItem.getRepositoryId()).thenReturn("myid");
        when(testRuleItem.getPropertyValue(RuleProperty.QUERY)).thenReturn("arc'teryx");
        SolrInputDocument doc = mgr.createRuleDocument(testRuleItem);
        assertEquals("myid", doc.getField("id").getValue());
        assertEquals("arc'teryx", doc.getField("query").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("siteId").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("catalogId").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("category").getValue());        
    }
    
    @Test
    public void testCreateRuleDocumentMulti() throws RepositoryException {
        // if you use a string for each attribute that will be checked
        RuleManager mgr = new RuleManager(repository, builder, server);
        when(testRuleItem.getRepositoryId()).thenReturn("howdy_id");
        when(testRuleItem.getPropertyValue(RuleProperty.QUERY)).thenReturn("arc'teryx");
        when(testRuleItem.getPropertyValue(RuleProperty.INCLUDE_SUBCATEGORIES)).thenReturn(Boolean.TRUE);
        
        when(testRuleItem.getPropertyValue(RuleProperty.SITES)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{ siteA, siteB, siteC})));
        when(testRuleItem.getPropertyValue(RuleProperty.CATALOGS)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{ cataA, cataB, cataC})));
        when(testRuleItem.getPropertyValue(RuleProperty.CATEGORIES)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{ cateA, cateB, cateC })));
        
        SolrInputDocument doc = mgr.createRuleDocument(testRuleItem);
        assertEquals("howdy_id", doc.getField("id").getValue());
        assertEquals("arc'teryx", doc.getField("query").getValue());

        @SuppressWarnings("unchecked")
        ArrayList<String> sites = (ArrayList<String>) doc.getField("siteId").getValue();
        assertThat(sites, hasItem("site:alpha"));
        assertThat(sites, hasItem("site:beta"));
        assertThat(sites, hasItem("site:charlie"));
        
        @SuppressWarnings("unchecked")
        ArrayList<String> catalogs = (ArrayList<String>) doc.getField("catalogId").getValue();
        assertThat(catalogs, hasItem("cata:alpha"));
        assertThat(catalogs, hasItem("cata:beta"));
        assertThat(catalogs, hasItem("cata:charlie"));
                        
        @SuppressWarnings("unchecked")
        ArrayList<String> categories = (ArrayList<String>) doc.getField("category").getValue();
        for (String token : new String[] {
                "cateA:token1",
                "cateA:token2",
                "cateB:token",
                "cateCchild1:token",
                "cateCchild2:token",
        }) {
            assertThat(categories, hasItem(token));
        }

    }
     
    @Test(expected=IllegalArgumentException.class)
    public void testLoadRulesEmptyQuery() throws RepositoryException, SolrServerException {
        RuleManager mgr = new RuleManager(repository, builder, server);
        mgr.loadRules("", null, "Men's Clothing", true, false, cataA);
    }  
    
    @Test
    public void testLoadRulesNullRule() throws RepositoryException, SolrServerException {  
        SolrDocumentList solrDocumentList = new SolrDocumentList();
        // ---------- set up docs with a rule type -----------
        setUpRuleData(null, "i wear shoes", "supra", facetRule, shoeItem, solrDocumentList);
        // note that we do NOT add this into the Repository so that we have a null rule in loadRules, this causes this document to not go into the rules
        SolrDocument rule = new SolrDocument();
        rule.addField("description", "avacado's grow on trees!");
        rule.addField("id", "avacado");
        solrDocumentList.add(rule);    
        setUpRuleData(null, "water bottle's are nice for drinking water!", "nalgene", boostRule, bottleItem, solrDocumentList);
                       
        // ----------- set up doclist attributes ----------
        solrDocumentList.setNumFound(solrDocumentList.size());
        solrDocumentList.setStart(0L);
//        solrDocumentList.setMaxScore(1000.0);
        when(queryResponse.getResults()).thenReturn(solrDocumentList);
        when(server.query(any(SolrParams.class))).thenReturn(queryResponse);
        
        // ----------- set up rule manager -------------
        
        RuleManager mgr = new RuleManager(repository, builder, server);
        assertEquals(null, mgr.getRules());
        
        // ------------ make the call to load the rules etc -------------
        mgr.loadRules("jackets", null, "Men's Clothing", true, false, cataA);
        
        // ------------ assertions about the rules that were generated ---------
        assertNotNull(mgr.getRules());
        
        Map<String, List<RepositoryItem>> rules = mgr.getRules();
        
        assertEquals(2, rules.size());
        assertThat(rules.keySet(), hasItem(facetRule));
        assertThat(rules.keySet(), hasItem(boostRule));

        List<RepositoryItem> facetItems = rules.get(facetRule);
        List<RepositoryItem> boostItems = rules.get(boostRule);

        assertEquals(1, facetItems.size());
        assertEquals(1, boostItems.size());

        assertThat(facetItems, hasItem(shoeItem));
        assertThat(boostItems, hasItem(bottleItem));
    }        
    
    @Test
    public void testLoadRulesMixedTypes() throws RepositoryException, SolrServerException {  
        SolrDocumentList solrDocumentList = new SolrDocumentList();
        // ---------- set up docs with a rule type -----------
        setUpRuleData(null, "i wear shoes", "supra", facetRule, shoeItem, solrDocumentList);
        setUpRuleData(null, "avacado's grow on trees!", "avacado", blockRule, fruitItem, solrDocumentList);    
        setUpRuleData(null, "water bottle's are nice for drinking water!", "nalgene", boostRule, bottleItem, solrDocumentList);
                       
        // ----------- set up doclist attributes ----------
        solrDocumentList.setNumFound(solrDocumentList.size()); 
        solrDocumentList.setStart(0L);
//        solrDocumentList.setMaxScore(1000.0);
        when(queryResponse.getResults()).thenReturn(solrDocumentList);
        when(server.query(any(SolrParams.class))).thenReturn(queryResponse);
        
        // ----------- set up rule manager -------------
        
        RuleManager mgr = new RuleManager(repository, builder, server);
        assertEquals(null, mgr.getRules());
        
        // ------------ make the call to load the rules etc -------------
        mgr.loadRules("jackets", null, "Men's Clothing", true, false, cataA);
        
        // ------------ assertions about the rules that were generated ---------
        assertNotNull(mgr.getRules());
        
        Map<String, List<RepositoryItem>> rules = mgr.getRules();
        
        assertEquals(3, rules.size());
        assertThat(rules.keySet(), hasItem(facetRule));
        assertThat(rules.keySet(), hasItem(boostRule));
        assertThat(rules.keySet(), hasItem(blockRule));

        List<RepositoryItem> facetItems = rules.get(facetRule);
        List<RepositoryItem> boostItems = rules.get(boostRule);
        List<RepositoryItem> blockItems = rules.get(blockRule);
        assertEquals(1, facetItems.size());
        assertEquals(1, boostItems.size());
        assertEquals(1, blockItems.size());
        assertThat(facetItems, hasItem(shoeItem));
        assertThat(boostItems, hasItem(bottleItem));
        assertThat(blockItems, hasItem(fruitItem));
    }
    
    @Test
    public void testLoadRulesPaging() throws RepositoryException, SolrServerException {
        // test paging over batches of results
        SolrDocumentList docList1 = new SolrDocumentList();
        SolrDocumentList docList2 = new SolrDocumentList();
        
        RepositoryItem bikeItem = mock(RepositoryItem.class);
        RepositoryItem sledItem = mock(RepositoryItem.class);
        RepositoryItem carItem = mock(RepositoryItem.class);
        RepositoryItem heliItem = mock(RepositoryItem.class);
        RepositoryItem coatItem = mock(RepositoryItem.class);
        RepositoryItem snowItem = mock(RepositoryItem.class);
        RepositoryItem farmItem = mock(RepositoryItem.class);
        RepositoryItem steakItem = mock(RepositoryItem.class);
        RepositoryItem pillowItem = mock(RepositoryItem.class);
         
        
        // ---------- set up docs with a rule type -----------
        setUpRuleData(null, "i wear shoes",     "supra",    facetRule, shoeItem,  docList1); // SKIPPING due to setStart
        setUpRuleData(null, "avacado's ",       "avacado",  blockRule, fruitItem, docList1);    
        setUpRuleData(null, "water bottles!",   "nalgene",  boostRule, bottleItem,docList1);
        setUpRuleData(null, "biking?  fun!",    "tallboy",  boostRule, bikeItem,  docList1);
        setUpRuleData(null, "sleds are lame",   "suzuki",   facetRule, sledItem,  docList1);
        setUpRuleData(null, "cars are lame",    "vw",       facetRule, carItem,   docList1);
        setUpRuleData(null, "fly in a heli",    "heli",     boostRule, heliItem,  docList2); // SKIPPING due to setStart
        setUpRuleData(null, "snow is fun",      "snow",     boostRule, snowItem,  docList2);
        setUpRuleData(null, "good for food",    "farm",     blockRule, farmItem,  docList2);
        setUpRuleData(null, "cows are food",    "steak",    facetRule, steakItem, docList2);
        setUpRuleData(null, "coatItem",         "patagonia",boostRule, coatItem,  docList2);
        setUpRuleData(null, "sleeping",         "pillow",   boostRule, pillowItem,docList2);
                       
        // ----------- set up doclist attributes ----------
        docList1.setNumFound(docList1.size() + docList2.size()); // set numfound to be both pagefuls...
        docList2.setNumFound(docList1.size() + docList2.size()); // set numfound to be both pagefuls...
        docList1.setStart(0L);
        docList2.setStart(0L);
//        solrDocumentList.setMaxScore(1000.0);
        QueryResponse queryResponse1 = mock(QueryResponse.class);
        QueryResponse queryResponse2 = mock(QueryResponse.class);
        
        when(queryResponse1.getResults()).thenReturn(docList1);
        when(queryResponse2.getResults()).thenReturn(docList2);
//        when(server.query(any(SolrParams.class))).thenReturn(queryResponse);
        when(server.query(any(SolrParams.class))).thenReturn(queryResponse1, queryResponse2);
        
        // ----------- set up rule manager -------------        
        RuleManager mgr = new RuleManager(repository, builder, server);
        assertEquals(null, mgr.getRules());
        
        // ------------ make the call to load the rules etc -------------
        mgr.loadRules("jackets", null, "Men's Clothing", true, false, cataA);
        
        // ------------ assertions about the rules that were generated ---------
        assertNotNull(mgr.getRules());
        
        Map<String, List<RepositoryItem>> rules = mgr.getRules();
        
        assertEquals(3, rules.size());
        assertThat(rules.keySet(), hasItem(facetRule));
        assertThat(rules.keySet(), hasItem(boostRule));
        assertThat(rules.keySet(), hasItem(blockRule));

        List<RepositoryItem> facetItems = rules.get(facetRule);
        List<RepositoryItem> boostItems = rules.get(boostRule);
        List<RepositoryItem> blockItems = rules.get(blockRule);
        
        assertEquals(4, facetItems.size());
        assertEquals(6, boostItems.size());
        assertEquals(2, blockItems.size());
        
        // test facets...
        for (RepositoryItem item : new RepositoryItem[]{sledItem, carItem, steakItem, }) {
            assertThat(facetItems, hasItem(item));    
        }
        
        // test boosts...
        for (RepositoryItem item : new RepositoryItem[]{bottleItem, bikeItem, snowItem, coatItem, pillowItem,  }) {
            assertThat(boostItems, hasItem(item));    
        }
        
        // test blocks...
        for (RepositoryItem item : new RepositoryItem[]{ fruitItem, farmItem, }) {
            assertThat(blockItems, hasItem(item));    
        }
    }
    
    @Test
    public void testLoadRulesFacets() throws RepositoryException, SolrServerException {  
        SolrDocumentList solrDocumentList = new SolrDocumentList();
        // ---------- set up docs with a rule type -----------
        setUpRuleData(null, "i wear shoes", "supra", facetRule, shoeItem, solrDocumentList);
        setUpRuleData(null, "avacado's grow on trees!", "avacado", facetRule, fruitItem, solrDocumentList);    
        setUpRuleData(null, "water bottle's are nice for drinking water!", "nalgene", facetRule, bottleItem, solrDocumentList);
                       
        // ----------- set up doclist attributes ----------
        solrDocumentList.setNumFound(solrDocumentList.size());
        solrDocumentList.setStart(0L);
//        solrDocumentList.setMaxScore(1000.0);
        when(queryResponse.getResults()).thenReturn(solrDocumentList);
        when(server.query(any(SolrParams.class))).thenReturn(queryResponse);
        
        // ----------- set up rule manager -------------
        
        RuleManager mgr = new RuleManager(repository, builder, server);
        assertEquals(null, mgr.getRules());
        
        // ------------ make the call to load the rules etc -------------
        mgr.loadRules("jackets", null, "Men's Clothing", true, false, cataA);
        
        // ------------ assertions about the rules that were generated ---------
        assertNotNull(mgr.getRules());        
        Map<String, List<RepositoryItem>> rules = mgr.getRules();        
        assertEquals(1, rules.size());
        assertEquals(facetRule, rules.keySet().iterator().next());        
        List<RepositoryItem> facetItems = rules.get(facetRule);        
        assertEquals(3, facetItems.size());
        assertThat(facetItems, hasItem(shoeItem));
        assertThat(facetItems, hasItem(fruitItem));
        assertThat(facetItems, hasItem(bottleItem));
    }        
    
    // finished
    @Test
    public void testLoadRulesVerifyQueryWithCategory() throws RepositoryException, SolrServerException {
        SolrDocumentList solrDocumentList = new SolrDocumentList();
        String category = "My super duper favorite Men's category";
        String searchQuery = "fantastic jackets )";
        String escapedSearchQuery = "fantastic\\ jackets\\ \\)";
        when(queryResponse.getResults()).thenReturn(solrDocumentList);
        when(server.query(any(SolrParams.class))).thenReturn(queryResponse);
        // ----------- set up rule manager -------------
        RuleManager mgr = new RuleManager(repository, builder, server);
        
        // ------------ make the call to load the rules etc -------------
        mgr.loadRules(searchQuery, null, category, true, false, cataA);
        
        // ------------ assertions about the inner solr query that was performed -----------
        ArgumentCaptor<SolrQuery> query = ArgumentCaptor.forClass(SolrQuery.class);
        verify(server).query(query.capture());
        List<String> filters = Arrays.asList(query.getValue().getFilterQueries());
        assertEquals(1, filters.size()); 
        assertEquals("(category:__all__ OR category:" + category + ") AND (siteId:__all__ OR siteId:site:alpha) AND (catalogId:__all__ OR catalogId:cata:alpha) AND -(((startDate:[* TO *]) AND -(startDate:[* TO NOW/DAY+1DAY])) OR (endDate:[* TO *] AND -endDate:[NOW/DAY+1DAY TO *]))", filters.get(0));
        assertEquals("(target:allpages OR target:searchpages) AND ((" + escapedSearchQuery + ")^2 OR query:__all__)", query.getValue().getQuery());
    }
    
    @Test
    public void testLoadRulesForCategoryPage() throws RepositoryException, SolrServerException {
        SolrDocumentList solrDocumentList = new SolrDocumentList();
        String category = "My category";
        String searchQuery = "catId:myCat";
        when(queryResponse.getResults()).thenReturn(solrDocumentList);
        when(server.query(any(SolrParams.class))).thenReturn(queryResponse);
        // ----------- set up rule manager -------------
        RuleManager mgr = new RuleManager(repository, builder, server);
        
        // ------------ make the call to load the rules etc -------------
        mgr.loadRules(searchQuery, null, category, false, false, cataA);
        
        // ------------ assertions about the inner solr query that was performed -----------
        ArgumentCaptor<SolrQuery> query = ArgumentCaptor.forClass(SolrQuery.class);
        verify(server).query(query.capture());
        List<String> filters = Arrays.asList(query.getValue().getFilterQueries());
        assertEquals(1, filters.size()); 
        assertEquals("(category:__all__ OR category:" + category + ") AND (siteId:__all__ OR siteId:site:alpha) AND (catalogId:__all__ OR catalogId:cata:alpha) AND -(((startDate:[* TO *]) AND -(startDate:[* TO NOW/DAY+1DAY])) OR (endDate:[* TO *] AND -endDate:[NOW/DAY+1DAY TO *]))", filters.get(0));
        assertEquals("(target:allpages OR target:categorypages)", query.getValue().getQuery());
    }
    
    @Test
    public void testLoadRulesVerifyQueryWithoutCategory() throws RepositoryException, SolrServerException {
        SolrDocumentList solrDocumentList = new SolrDocumentList();
        String category = "";
        String searchQuery = "fantastic jackets";
        String escapedSearchQuery = "fantastic\\ jackets";
        when(queryResponse.getResults()).thenReturn(solrDocumentList);
        when(server.query(any(SolrParams.class))).thenReturn(queryResponse);
        // ----------- set up rule manager -------------
        RuleManager mgr = new RuleManager(repository, builder, server);
        
        // ------------ make the call to load the rules etc -------------
        mgr.loadRules(searchQuery, null, category, true, false, cataA);
        
        // ------------ assertions about the inner solr query that was performed -----------
        ArgumentCaptor<SolrQuery> query = ArgumentCaptor.forClass(SolrQuery.class);
        verify(server).query(query.capture());
        List<String> filters = Arrays.asList(query.getValue().getFilterQueries());
        assertEquals(1, filters.size()); 
        assertEquals("(category:__all__) AND (siteId:__all__ OR siteId:site:alpha) AND (catalogId:__all__ OR catalogId:cata:alpha) AND -(((startDate:[* TO *]) AND -(startDate:[* TO NOW/DAY+1DAY])) OR (endDate:[* TO *] AND -endDate:[NOW/DAY+1DAY TO *]))", filters.get(0));
        assertEquals("(target:allpages OR target:searchpages) AND ((" + escapedSearchQuery + ")^2 OR query:__all__)", query.getValue().getQuery());
    }
    
    // finished
    @Test
    public void testLoadRulesNullResults() throws RepositoryException, SolrServerException {
        // test the null queryResponse path
        when(queryResponse.getResults()).thenReturn(null);
        when(server.query(any(SolrParams.class))).thenReturn(queryResponse);
        
        // ----------- set up rule manager -------------        
        RuleManager mgr = new RuleManager(repository, builder, server);
        assertEquals(null, mgr.getRules());
        
        // ------------ make the call to load the rules etc -------------
        mgr.loadRules("pants", null, "Women's Clothing", true, false, cataA);
        assertNotNull(mgr.getRules());
        assertEquals(0, mgr.getRules().size());
    }
    
    // finished
    @Test
    public void testLoadRulesEmptyResults() throws RepositoryException, SolrServerException {
        // test the empty queryResponse path
        SolrDocumentList solrDocumentList = new SolrDocumentList();        
        when(queryResponse.getResults()).thenReturn(solrDocumentList);
        when(server.query(any(SolrParams.class))).thenReturn(queryResponse);
        
        // ----------- set up rule manager -------------        
        RuleManager mgr = new RuleManager(repository, builder, server);
        assertEquals(null, mgr.getRules());
        
        // ------------ make the call to load the rules etc -------------
        mgr.loadRules("pants", null, "Women's Clothing", true, false, cataA);
        assertNotNull(mgr.getRules());
        assertEquals(0, mgr.getRules().size());
    }

    @Test
    public void testRankingRuleSimpleBrandRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        RepositoryItem expression = mockRule("brand", 1, "88", null);

        expresionList.add(expression);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.CONDITIONS)).thenReturn(expresionList);
        when(testRuleItem.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn("rankingRule");
        when(testRuleItem.getPropertyValue(RankingRuleProperty.BOOST_BY)).thenReturn(RankingRuleProperty.BOOST_BY_FACTOR);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.STRENGTH)).thenReturn(RankingRuleProperty.STRENGTH_MAXIMUM_BOOST);

        RuleManager mgr = new RuleManager(repository, builder, server);
        SolrInputDocument doc = mgr.createRuleDocument(testRuleItem);

        assertEquals("if(exists(query({!lucene v='(brandId:88)'})),10.0,1.0)", (String) doc.getFieldValue(RuleManager.FIELD_BOOST_FUNCTION));
    }

    @Test
    public void testRankingRuleSimplePercentageRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        RepositoryItem expression = mockRule("pct_off", 1, "15", null);

        expresionList.add(expression);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.CONDITIONS)).thenReturn(expresionList);
        when(testRuleItem.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn("rankingRule");
        when(testRuleItem.getPropertyValue(RankingRuleProperty.BOOST_BY)).thenReturn(RankingRuleProperty.BOOST_BY_FACTOR);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.STRENGTH)).thenReturn(RankingRuleProperty.STRENGTH_MAXIMUM_DEMOTE);

        RuleManager mgr = new RuleManager(repository, builder, server);
        SolrInputDocument doc = mgr.createRuleDocument(testRuleItem);

        assertEquals("if(exists(query({!lucene v='(discountPercentUS:[15 TO 100])'})),0.1,1.0)", (String) doc.getFieldValue(RuleManager.FIELD_BOOST_FUNCTION));
    }

    @Test
    public void testRankingRuleSimpleGenderRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        RepositoryItem expression = mockRule("gender", 1, "Boys'", null);

        expresionList.add(expression);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.CONDITIONS)).thenReturn(expresionList);
        when(testRuleItem.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn("rankingRule");
        when(testRuleItem.getPropertyValue(RankingRuleProperty.BOOST_BY)).thenReturn(RankingRuleProperty.BOOST_BY_ATTRIBUTE_VALUE);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.ATTRIBUTE)).thenReturn("listRank");
        RuleManager mgr = new RuleManager(repository, builder, server);
        SolrInputDocument doc = mgr.createRuleDocument(testRuleItem);

        assertEquals("if(exists(query({!lucene v='(gender:Boys\\')'})),listRank,1.0)", (String) doc.getFieldValue(RuleManager.FIELD_BOOST_FUNCTION));
    }

    @Test
    public void testRankingRuleSimpleShowSaleRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        RepositoryItem expression = mockRule("show_sale", 1, "false", null);

        expresionList.add(expression);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.CONDITIONS)).thenReturn(expresionList);
        when(testRuleItem.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn("rankingRule");
        when(testRuleItem.getPropertyValue(RankingRuleProperty.BOOST_BY)).thenReturn(RankingRuleProperty.BOOST_BY_FACTOR);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.STRENGTH)).thenReturn(RankingRuleProperty.STRENGTH_MEDIUM_DEMOTE);
        RuleManager mgr = new RuleManager(repository, builder, server);
        SolrInputDocument doc = mgr.createRuleDocument(testRuleItem);

        assertEquals("if(exists(query({!lucene v='(onsaleUS:false)'})),0.5,1.0)", (String) doc.getFieldValue(RuleManager.FIELD_BOOST_FUNCTION));
    }

    @Test
    public void testRankingRuleSimplePastSeasonRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        RepositoryItem expression = mockRule("past_season", 1, "false", null);

        expresionList.add(expression);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.CONDITIONS)).thenReturn(expresionList);
        when(testRuleItem.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn("rankingRule");
        when(testRuleItem.getPropertyValue(RankingRuleProperty.BOOST_BY)).thenReturn(RankingRuleProperty.BOOST_BY_FACTOR);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.STRENGTH)).thenReturn(RankingRuleProperty.STRENGTH_MEDIUM_BOOST);
        RuleManager mgr = new RuleManager(repository, builder, server);
        SolrInputDocument doc = mgr.createRuleDocument(testRuleItem);

        assertEquals("if(exists(query({!lucene v='(isPastSeason:false)'})),2.0,1.0)", (String) doc.getFieldValue(RuleManager.FIELD_BOOST_FUNCTION));
    }

    @Test
    public void testRankingRuleSimpleBrandPastSeasonRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("past_season", 1, "false", null));
        expresionList.add(mockRule("brand", 1, "88", "AND"));

        when(testRuleItem.getPropertyValue(RankingRuleProperty.CONDITIONS)).thenReturn(expresionList);
        when(testRuleItem.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn("rankingRule");
        when(testRuleItem.getPropertyValue(RankingRuleProperty.BOOST_BY)).thenReturn(RankingRuleProperty.BOOST_BY_FACTOR);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.STRENGTH)).thenReturn(RankingRuleProperty.STRENGTH_MEDIUM_BOOST);
        RuleManager mgr = new RuleManager(repository, builder, server);
        SolrInputDocument doc = mgr.createRuleDocument(testRuleItem);

        assertEquals("if(exists(query({!lucene v='(isPastSeason:false AND brandId:88)'})),2.0,1.0)", (String) doc.getFieldValue(RuleManager.FIELD_BOOST_FUNCTION));
    }

    @Test
    public void testRankingRuleSimpleKeywordRule() throws RepositoryException {

        String keyword = "this is a big weird keyword'";
        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        RepositoryItem expression = mockRule("keyword", 1, keyword, null);

        expresionList.add(expression);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.CONDITIONS)).thenReturn(expresionList);
        when(testRuleItem.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn("rankingRule");
        when(testRuleItem.getPropertyValue(RankingRuleProperty.BOOST_BY)).thenReturn(RankingRuleProperty.BOOST_BY_FACTOR);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.STRENGTH)).thenReturn(RankingRuleProperty.STRENGTH_WEAK_BOOST);
        RuleManager mgr = new RuleManager(repository, builder, server);
        SolrInputDocument doc = mgr.createRuleDocument(testRuleItem);

        assertEquals("if(exists(query({!lucene v='(keyword:this\\ is\\ a\\ big\\ weird\\ keyword\\')'})),1.5,1.0)",
                (String) doc.getFieldValue(RuleManager.FIELD_BOOST_FUNCTION));
    }

    @Test
    public void testRankingRuleNoConditions() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();

        when(testRuleItem.getPropertyValue(RankingRuleProperty.CONDITIONS)).thenReturn(expresionList);
        when(testRuleItem.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn("rankingRule");
        when(testRuleItem.getPropertyValue(RankingRuleProperty.BOOST_BY)).thenReturn(RankingRuleProperty.BOOST_BY_ATTRIBUTE_VALUE);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.ATTRIBUTE)).thenReturn("div(1,listRank)");
        RuleManager mgr = new RuleManager(repository, builder, server);
        SolrInputDocument doc = mgr.createRuleDocument(testRuleItem);

        assertEquals("div(1,listRank)", (String) doc.getFieldValue(RuleManager.FIELD_BOOST_FUNCTION));
    }

    @Test
    public void testRankingRuleComplexRule() throws RepositoryException {

        String keyword = "this is a big weird keyword'";
        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("brand", 1, "88", null));
        expresionList.add(mockRule("brand", 2, "77", "OR"));
        expresionList.add(mockRule("show_sale", 2, "true", "AND"));
        expresionList.add(mockRule("past_season", 3, "false", "AND"));

        when(testRuleItem.getPropertyValue(RankingRuleProperty.CONDITIONS)).thenReturn(expresionList);
        when(testRuleItem.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn("rankingRule");
        when(testRuleItem.getPropertyValue(RankingRuleProperty.BOOST_BY)).thenReturn(RankingRuleProperty.BOOST_BY_FACTOR);
        when(testRuleItem.getPropertyValue(RankingRuleProperty.STRENGTH)).thenReturn(RankingRuleProperty.STRENGTH_WEAK_BOOST);
        RuleManager mgr = new RuleManager(repository, builder, server);
        SolrInputDocument doc = mgr.createRuleDocument(testRuleItem);

        assertEquals("if(exists(query({!lucene v='(brandId:88 OR (brandId:77 AND onsaleUS:true AND (isPastSeason:false)))'})),1.5,1.0)",
                (String) doc.getFieldValue(RuleManager.FIELD_BOOST_FUNCTION));
    }
    

    @Test
    public void testDefaultBoostFactors() {
        RuleManager mgr = new RuleManager(repository, builder, server);

        assertEquals(Float.toString(1/10f), mgr.mapStrength(RankingRuleProperty.STRENGTH_MAXIMUM_DEMOTE));
        assertEquals(Float.toString(1/5f), mgr.mapStrength(RankingRuleProperty.STRENGTH_STRONG_DEMOTE));
        assertEquals(Float.toString(1/2f), mgr.mapStrength(RankingRuleProperty.STRENGTH_MEDIUM_DEMOTE));
        assertEquals(Float.toString(1/1.5f), mgr.mapStrength(RankingRuleProperty.STRENGTH_WEAK_DEMOTE));
        assertEquals(Float.toString(1.0f), mgr.mapStrength(RankingRuleProperty.STRENGTH_NEUTRAL));
        assertEquals(Float.toString(1.5f), mgr.mapStrength(RankingRuleProperty.STRENGTH_WEAK_BOOST));
        assertEquals(Float.toString(2f), mgr.mapStrength(RankingRuleProperty.STRENGTH_MEDIUM_BOOST));
        assertEquals(Float.toString(5f), mgr.mapStrength(RankingRuleProperty.STRENGTH_STRONG_BOOST));
        assertEquals(Float.toString(10f), mgr.mapStrength(RankingRuleProperty.STRENGTH_MAXIMUM_BOOST));

    }

    @Test
    public void testFacetRuleSorting() {
        final FacetManager facetManager = mock(FacetManager.class);
        RuleManager mgr = new RuleManager(repository, builder, server) {
            @Override
            public FacetManager getFacetManager() {
                return facetManager;
            }
        };
        SolrQuery query = mock(SolrQuery.class);
        Map<String, List<RepositoryItem>> typeToRules = new HashMap<String, List<RepositoryItem>>();

        List<RepositoryItem> facetRules = new ArrayList<RepositoryItem>();
        typeToRules.put(facetRule, facetRules);
        RepositoryItem facetRule1 = mock(RepositoryItem.class);
        when(facetRule1.getRepositoryId()).thenReturn("facetRule1");
        facetRules.add(facetRule1);

        RepositoryItem facet1 = mock(RepositoryItem.class, "facet1");
        RepositoryItem facet2 = mock(RepositoryItem.class, "facet2");
        RepositoryItem facet3 = mock(RepositoryItem.class, "facet3");

        when(facetRule1.getPropertyValue(FacetRuleProperty.FACETS)).thenReturn(Arrays.asList(facet1, facet2, facet3));

        Map<String, SolrDocument> ruleDocs = new HashMap<String, SolrDocument>();
        SolrDocument facetRuleDoc = mock(SolrDocument.class);
        when(facetRuleDoc.getFieldValue(FacetRuleProperty.COMBINE_MODE)).thenReturn(FacetRuleProperty.COMBINE_MODE_REPLACE);
        ruleDocs.put("facetRule1", facetRuleDoc);


        mgr.setRuleParams(query, typeToRules, ruleDocs);
        verify(facetManager).clear();
        verify(facetManager).addFacet(facet1);
        verify(facetManager).addFacet(facet2);
        verify(facetManager).addFacet(facet3);
        verify(facetManager).setParams(query);
        verifyNoMoreInteractions(facetManager);
    }

    @Test
    public void testFacetRuleSortingReplace() {
        testFacetRuleSortingAux(FacetRuleProperty.COMBINE_MODE_REPLACE, 2);
    }

    @Test
    public void testFacetRuleSortingAppend() {
        testFacetRuleSortingAux(FacetRuleProperty.COMBINE_MODE_APPEND, 0);
    }

    private void testFacetRuleSortingAux(String combineMode, int clearTimes) {
        final FacetManager facetManager = mock(FacetManager.class);
        RuleManager mgr = new RuleManager(repository, builder, server) {
            @Override
            public FacetManager getFacetManager() {
                return facetManager;
            }
        };
        SolrQuery query = mock(SolrQuery.class);
        Map<String, List<RepositoryItem>> typeToRules = new HashMap<String, List<RepositoryItem>>();

        List<RepositoryItem> facetRules = new ArrayList<RepositoryItem>();
        Map<String, SolrDocument> ruleDocs = new HashMap<String, SolrDocument>();


        // first rule
        RepositoryItem facetRule1 = mock(RepositoryItem.class, "facetRule1");
        when(facetRule1.getRepositoryId()).thenReturn("facetRule1");
        facetRules.add(facetRule1);

        SolrDocument facetRuleDoc = mock(SolrDocument.class);
        when(facetRuleDoc.getFieldValue(FacetRuleProperty.COMBINE_MODE)).thenReturn(combineMode);
        ruleDocs.put("facetRule1", facetRuleDoc);

        RepositoryItem facet1 = mock(RepositoryItem.class, "facet1");
        RepositoryItem facet2 = mock(RepositoryItem.class, "facet2");
        RepositoryItem facet3 = mock(RepositoryItem.class, "facet3");

        when(facetRule1.getPropertyValue(FacetRuleProperty.FACETS)).thenReturn(Arrays.asList(facet1, facet2, facet3));
        typeToRules.put(facetRule, facetRules);

        // second rule
        RepositoryItem facetRule2 = mock(RepositoryItem.class, "facetRule2");
        when(facetRule2.getRepositoryId()).thenReturn("facetRule2");
        facetRules.add(facetRule2);

        SolrDocument facetRuleDoc2 = mock(SolrDocument.class);
        when(facetRuleDoc2.getFieldValue(FacetRuleProperty.COMBINE_MODE)).thenReturn(combineMode);
        ruleDocs.put("facetRule2", facetRuleDoc2);

        RepositoryItem facet4 = mock(RepositoryItem.class, "facet4");
        RepositoryItem facet5 = mock(RepositoryItem.class, "facet5");
        RepositoryItem facet6 = mock(RepositoryItem.class, "facet6");

        when(facetRule2.getPropertyValue(FacetRuleProperty.FACETS)).thenReturn(Arrays.asList(facet4, facet5, facet6));
        typeToRules.put(facetRule, facetRules);

        mgr.setRuleParams(query, typeToRules, ruleDocs);
        verify(facetManager, times(clearTimes)).clear();
        verify(facetManager).addFacet(facet1);
        verify(facetManager).addFacet(facet2);
        verify(facetManager).addFacet(facet3);
        verify(facetManager).addFacet(facet4);
        verify(facetManager).addFacet(facet5);
        verify(facetManager).addFacet(facet6);
        verify(facetManager).setParams(query);
        verifyNoMoreInteractions(facetManager);
    }
    
    @Test
    public void testCateGoryPathRegularCategoryWithSubCategories() throws RepositoryException {
        RuleManager mgr = new RuleManager(repository, builder, server);
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
        SolrInputDocument doc = mgr.createRuleDocument(testRuleItem);
        List<String> calculatedPaths = (List<String>)doc.getField("category").getValue();
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
    public void testCateGoryPathRegularCategoryWithOutSubCategories() throws RepositoryException {
        RuleManager mgr = new RuleManager(repository, builder, server);
        when(testRuleItem.getRepositoryId()).thenReturn("superduper");
        when(testRuleItem.getPropertyValue(RuleProperty.CATEGORIES)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{cateCchild1, })));
        when(cateCchild1.getItemDescriptor().getItemDescriptorName()).thenReturn(CategoryProperty.ITEM_DESCRIPTOR);
        when(cateCchild1.getRepositoryId()).thenReturn("cateCchild1");        
        when(cateCchild1.getPropertyValue(CategoryProperty.FIXED_PARENT_CATEGORIES)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{cateC, })));
        when(cateC.getRepositoryId()).thenReturn("cateC");
        when(cateC.getPropertyValue(CategoryProperty.PARENT_CATALOGS)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{cataC, cataB, })));
        when(cataC.getRepositoryId()).thenReturn("cataC");
        when(cataB.getRepositoryId()).thenReturn("cataB");
        SolrInputDocument doc = mgr.createRuleDocument(testRuleItem);
        List<String> calculatedPaths = (List<String>)doc.getField("category").getValue();
        assertEquals("cateCchild1:token", calculatedPaths.get(0));
        assertEquals("cataC.cateCchild1", calculatedPaths.get(1));
        assertEquals("cataB.cateCchild1", calculatedPaths.get(2));
    }

}

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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import atg.multisite.Site;
import atg.repository.Repository;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.Group;
import org.apache.solr.client.solrj.response.GroupCommand;
import org.apache.solr.client.solrj.response.GroupResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.junit.Before;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.opencommercesearch.junit.SearchTest;
import org.opencommercesearch.junit.runners.SearchJUnit4ClassRunner;
import org.opencommercesearch.repository.FacetProperty;
import org.opencommercesearch.repository.FacetRuleProperty;
import org.opencommercesearch.repository.FieldFacetProperty;
import org.opencommercesearch.repository.RedirectRuleProperty;
import org.opencommercesearch.repository.RuleProperty;
import org.opencommercesearch.repository.SearchRepositoryItemDescriptor;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author rmerizalde
 */
@Category(IntegrationSearchTest.class)
@RunWith(SearchJUnit4ClassRunner.class)
public class AbstractSearchServerIntegrationTest {

    public static final int ROWS = 20;

    @Mock
    private Site site;

    @Mock
    private RepositoryItem catalog;

    @Mock
    private RepositoryItem redirectRule;
    
    @Mock
    private Repository searchRepository;
    
    @Mock
    private RulesBuilder rulesBuilder;
    
    @Before
    public void setUp() throws RepositoryException {
        initMocks(this);
        when(site.getRepositoryId()).thenReturn("outdoorSite");
        when(site.getPropertyValue("defaultCatalog")).thenReturn(catalog);
        when(catalog.getRepositoryId()).thenReturn("mycatalog");
        
        when(searchRepository.getItem("redirectRuleId", SearchRepositoryItemDescriptor.RULE)).thenReturn(redirectRule);
        when(redirectRule.getPropertyValue(RedirectRuleProperty.URL)).thenReturn("/redirect");
        when(redirectRule.getPropertyValue(RedirectRuleProperty.RULE_TYPE)).thenReturn("redirectRule");
    }

    @SearchTest(newInstance = true, productData = "/product_catalog/sandal.xml")
    public void testSearchCategoryName(SearchServer server) throws SearchServerException {
        testSearchCategoryAux(server, "shoe", "TNF3137");
    }

    @SearchTest(newInstance = true, productData = "/product_catalog/sandal.xml")
    public void testSearchCategoryNameSynonyms(SearchServer server) throws SearchServerException {
        testSearchCategoryAux(server, "sneakers", "TNF3137");
    }
    
    @SearchTest(newInstance = true, rulesData = "/rules/redirect.xml")
    public void testSearchSedirect(SearchServer server) throws SearchServerException {
        AbstractSearchServer baseServer = (AbstractSearchServer) server;
        baseServer.setSearchRepository(searchRepository);
        
        SolrQuery query = new SolrQuery("redirect");
        SearchResponse res = server.search(query, site);
        assertEquals("/redirect", res.getRedirectResponse());
    }

    
    
    @SearchTest(newInstance = true, productData = "/product_catalog/sandal.xml")
    public void testBrowseCategory(SearchServer server) throws SearchServerException {
        //scenario where we want to display only the top level categories. no results
        BrowseOptions options = new BrowseOptions(true, false, false, false,  100, null, "cat3000003", "mycatalog.cat3000003", "mycatalog");                
        SolrQuery query = new SolrQuery();
        SearchResponse response = server.browse(options, query, site, Locale.US, null);
        
        assertNull(response.getQueryResponse().getGroupResponse());
        validateFilterByCat3000003(response);
        validateCategoryPathNotInFacets(response);
        
        //scenario where we want to show results. not only display the top level categories
        options = new BrowseOptions(false, false, false, false, 100, null, "cat3000003", "mycatalog.cat3000003", "mycatalog");                
        query = new SolrQuery();
        response = server.browse(options, query, site, Locale.US, null);
        
        validateCategoryPathNotInFacets(response);
        assertNull(response.getQueryResponse().getGroupResponse());
        assertNull(response.getCategoryGraph());

    }


    @SearchTest(newInstance = true, productData = "/product_catalog/sandal.xml")
    public void testBrowseBrand(SearchServer server) throws SearchServerException {
        //scenario where we want to display only the top level categories for products that have a specific brand
        BrowseOptions options = new BrowseOptions(true, false, false,false,  100, "88", null, null, "mycatalog");
        SolrQuery query = new SolrQuery();
        SearchResponse response = server.browse(options, query, site, Locale.US, null);
        
        assertNull(response.getQueryResponse().getGroupResponse());
        validateFilterByTopLevelCat(response, options.isFetchProducts());
        validateCategoryPathNotInFacets(response);
        
        //scenario where we want to show results for products from a given brand. Not only display the top level categories
        options = new BrowseOptions(false, true, false, false, 100, "88", null, null, "mycatalog");
        query = new SolrQuery();
        query.setRows(ROWS);
        response = server.browse(options, query, site, Locale.US, null);
        
        assertEquals(1, response.getQueryResponse().getGroupResponse().getValues().size());
        assertEquals("The North Face", getFirstResponseProperty(response, "brand"));
        validateFilterByTopLevelCat(response, options.isFetchProducts());
        validateCategoryPathNotInFacets(response);
        
        //scenario where we want to show results for products from a given brand and category. Not only display the top level categories
        options = new BrowseOptions(false, true, false, false, 100, "88", "cat3000003", "mycatalog.cat3000003", "mycatalog");                
        query = new SolrQuery();
        query.setRows(ROWS);
        response = server.browse(options, query, site, Locale.US, null);
        
        assertEquals(1, response.getQueryResponse().getGroupResponse().getValues().size());
        assertEquals("The North Face", getFirstResponseProperty(response, "brand"));
        validateFilterByCat3000003(response);
        validateCategoryPathNotInFacets(response);

    }

    
    @SearchTest(newInstance = true, productData = "/product_catalog/sandal.xml")
    public void testOnSale(SearchServer server) throws SearchServerException {
        
        //scenario where we want to display only the top level categories for products that are on sale.
        BrowseOptions options = new BrowseOptions(true, false, true, false, 100, null, null, null, "mycatalog");                
        SolrQuery query = new SolrQuery();
        SearchResponse response = server.browse(options, query, site, Locale.US, null);
        
        assertNull(response.getQueryResponse().getGroupResponse());
        validateFilterByTopLevelCat(response, options.isFetchProducts());
        validateCategoryPathNotInFacets(response);
        
        //scenario where we want to show results for products that are on sale. not only display the top level categories
        options = new BrowseOptions(false, true, true, false, 100, null, null, null, "mycatalog");                
        query = new SolrQuery();
        query.setFields("id");
        query.setRows(ROWS);
        response = server.browse(options, query, site, Locale.US, null);
        
        validateCategoryPathNotInFacets(response);
        assertEquals(1, response.getQueryResponse().getGroupResponse().getValues().size());
        assertEquals("TNF3137-FUPINYL-S1", getFirstResponseProperty(response, "id"));
        assertNull(response.getCategoryGraph());
        
    }

    @SearchTest(newInstance = true, productData = "/product_catalog/sandal.xml")
    public void testRuleBasedCategory(SearchServer server) throws SearchServerException {
        
        AbstractSearchServer abstractServer = (AbstractSearchServer) server;
        abstractServer.setRulesBuilder(rulesBuilder);
        
        //scenario where we want to display a rule based category that shows product with a discount > 15%
        BrowseOptions options = new BrowseOptions(false, false, false, true,  100, null, "cat3000003", null, "mycatalog");     
        when(rulesBuilder.buildRulesFilter(options.getCategoryId(), Locale.US)).thenReturn("(categoryId:ruleCategory) OR (discountPercentUS:[15 TO 100])");
        SolrQuery query = new SolrQuery();
        query.setRows(ROWS);
        query.setFields("id");
        SearchResponse response = server.browse(options, query, site, Locale.US, null);
        
        assertEquals(1, response.getQueryResponse().getGroupResponse().getValues().size());
        assertEquals("TNF3137-FUPINYL-S1", response.getQueryResponse().getGroupResponse().getValues().get(0).getValues().get(0).getResult().get(0).getFieldValue("id"));

    }

    @SearchTest(newInstance = true, productData = "/product_catalog/sandal.xml")
    public void testCorrectedTermsAllMatches(SearchServer server) throws SearchServerException {   
        //validate term correction from the title
        validateCorrectedTerms(server, "basa", "base", true);        
        //validate terms correction from the category
        validateCorrectedTerms(server, "fotwear", "footwear", true);
        //validate terms correction from the brand
        validateCorrectedTerms(server, "nort face", "north face", true);
        //validate terms correction from the features
        validateCorrectedTerms(server, "ruber", "rubber", true);
        //validate terms correction from phrases with multiple entries
        validateCorrectedTerms(server, "sandl footwear", "sandal footwear", true);
    }
    
    @SearchTest(newInstance = true, productData = "/product_catalog/sandal.xml")
    public void testCorrectedTermsAnyMatches(SearchServer server) throws SearchServerException {           
        //scenario where it needs to do a search by any terms because we don't have jacket in the index
        validateCorrectedTerms(server, "jckt footwear", "jckt footwear", false);
    }
    
    @SearchTest(newInstance = true)
    public void testDeleteByQuery(SearchServer server) throws SearchServerException {
        SolrQuery query = new SolrQuery("jacket");
        query.setRows(ROWS);
        SearchResponse res = server.search(query, site);
        QueryResponse queryResponse = res.getQueryResponse();
        GroupResponse groupResponse = queryResponse.getGroupResponse();

        assertNotEquals(new Integer(1), groupResponse.getValues().get(0).getNGroups());

        server.deleteByQuery("*:*");
        server.commit();
        res = server.search(query, site);
        queryResponse = res.getQueryResponse();
        groupResponse = queryResponse.getGroupResponse();
        assertEquals(new Integer(0), groupResponse.getValues().get(0).getNGroups());
    }
    
    @SearchTest(newInstance = true, productData = "/product_catalog/sandal.xml", rulesData = "/rules/facetBlacklist.xml")
    public void testFacetBlacklist(SearchServer server) throws SearchServerException, RepositoryException {
        // Scenario to validate that the facet blacklist removes facet filter items.
        
        // The test creates a global facet rule, in the rules collection, with the scale and brand facets.
        // The test data has two values for scale: "US Kids Footwear" and "UK Kids Footwear". For this facet, we are
        // adding a blacklist that should remove the "UK Kids Footwear".
        // For the brand facet, the test data only has one entry: "The North Face" and we defined a 
        // blacklist with "Fake Brand". Because the terms don't match, then we shouldn't filter any entry.
        
        AbstractSearchServer baseServer = (AbstractSearchServer) server;
        baseServer.setSearchRepository(searchRepository);
        
        RepositoryItem facetRepoItem = mock(RepositoryItem.class);
        when(searchRepository.getItem("facetRuleId", SearchRepositoryItemDescriptor.RULE)).thenReturn(facetRepoItem);
        when(facetRepoItem.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn("facetRule");
        when(facetRepoItem.getRepositoryId()).thenReturn("facetRuleId");
        
        List<RepositoryItem> facetList = new ArrayList<RepositoryItem>();
        facetList.add(mockFacet("scale", "scale", "fieldFacet", "UK Kids Footwear"));
        facetList.add(mockFacet("brand", "brand", "fieldFacet", "Fake Brand"));
        when(facetRepoItem.getPropertyValue(FacetRuleProperty.FACETS)).thenReturn(facetList);
        
        
        SolrQuery query = new SolrQuery("shoe");
        query.setRows(ROWS);
        SearchResponse response = server.search(query, site);
        
        
        for(Facet facet :response.getFacets()){
            if(facet.getName().equals("scale")) {
                assertEquals(facet.getFilters().size(), 1);
                assertEquals(facet.getFilters().get(0).getName(), "US Kids Footwear");
            }
            if(facet.getName().equals("brand")) {
                assertEquals(facet.getFilters().size(), 1);
                assertEquals(facet.getFilters().get(0).getName(), "The North Face");
            }
        }

    }
    
    protected void validateFilterByTopLevelCat(SearchResponse response, boolean hasProducts) {
        if (hasProducts) {
            assertEquals(1, response.getQueryResponse().getGroupResponse().getValues().size());
        } else {
            assertNull(response.getQueryResponse().getGroupResponse());
        }
        assertEquals("cat3000003",   response.getCategoryGraph().get(0).getId());
        assertEquals("cat3100004",   response.getCategoryGraph().get(0).getCategoryGraphNodes().get(0).getId());
        assertEquals("cat31000077",  response.getCategoryGraph().get(0).getCategoryGraphNodes().get(1).getId());
        assertEquals("cat4000003",   response.getCategoryGraph().get(1).getId());
        assertEquals("cat410000178", response.getCategoryGraph().get(1).getCategoryGraphNodes().get(0).getId());
    }
    
    protected void validateFilterByCat3000003(SearchResponse response) {
        assertEquals("cat3100004",  response.getCategoryGraph().get(0).getId());
        assertEquals("cat411000179",response.getCategoryGraph().get(0).getCategoryGraphNodes().get(0).getId());
        assertEquals("categoryPath:mycatalog.cat3000003.cat3100004.cat411000179", 
                response.getCategoryGraph().get(0).getCategoryGraphNodes().get(0).getPath());
        
        assertEquals("cat31000077", response.getCategoryGraph().get(1).getId());
        assertEquals("cat411000196",response.getCategoryGraph().get(1).getCategoryGraphNodes().get(0).getId());
        assertEquals("categoryPath:mycatalog.cat3000003.cat31000077.cat411000196", 
                response.getCategoryGraph().get(1).getCategoryGraphNodes().get(0).getPath());
    }
    
    protected void validateCategoryPathNotInFacets(SearchResponse response) {
        for(Facet facet :response.getFacets()){
            if(facet.getName().equals("categoryPath")) {
                fail("facet's shouldn't contain category path");
            }
        }
    }
    
    private void testSearchCategoryAux(SearchServer server, String term, String expectedProductId) throws SearchServerException {
        SolrQuery query = new SolrQuery(term);
        query.setRows(ROWS);
        SearchResponse res = server.search(query, site);
        
        assertEquals(null, res.getCorrectedTerm());
        assertEquals(true, res.matchesAll());     
        
        QueryResponse queryResponse = res.getQueryResponse();
        GroupResponse groupResponse = queryResponse.getGroupResponse();

        for (GroupCommand command : groupResponse.getValues()) {
            for (Group group : command.getValues()) {
                String productId = group.getGroupValue();
                if (expectedProductId.equals(productId)) {
                    return;
                }
            }
        }
        fail("Product TNF3137 not found");
    }
    
    private String getFirstResponseProperty(SearchResponse response, String property) {
        return response.getQueryResponse().getGroupResponse().getValues().get(0).getValues().get(0).getResult().get(0).getFieldValue(property).toString();
    }
    
    private void validateCorrectedTerms(SearchServer server, String incorrectTerm, String expectedCorrectedTerm, boolean matchesAll) throws SearchServerException{
        SolrQuery query = new SolrQuery(incorrectTerm);
        query.setRows(ROWS);
        SearchResponse res = server.search(query, site);
        //SimpleOrderedMap params =  (SimpleOrderedMap) res.getQueryResponse().getHeader().get("params");
        //assertEquals(expectedCorrectedTerm, params.get("q"));
        assertEquals(expectedCorrectedTerm, res.getCorrectedTerm());
        assertEquals(matchesAll, res.matchesAll());  
    }
    
    private RepositoryItem mockFacet(String name, String field, String type, String blacklist){
        RepositoryItem facet = mock(RepositoryItem.class);
        when(facet.getRepositoryId()).thenReturn(name);
        when(facet.getPropertyValue(FacetProperty.TYPE)).thenReturn(type);
        when(facet.getPropertyValue(FacetProperty.NAME)).thenReturn(name);
        when(facet.getPropertyValue(FieldFacetProperty.FIELD)).thenReturn(field);
        if(StringUtils.isNotBlank(blacklist)) {
            Set<String> blackListSet = new HashSet<String>();
            blackListSet.add(blacklist);
            when(facet.getPropertyValue(FieldFacetProperty.BLACKLIST)).thenReturn(blackListSet);
        }
        return facet;
    }
}

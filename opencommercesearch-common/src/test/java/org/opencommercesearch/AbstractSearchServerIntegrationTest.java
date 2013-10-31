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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import atg.multisite.Site;
import atg.repository.Repository;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.Group;
import org.apache.solr.client.solrj.response.GroupCommand;
import org.apache.solr.client.solrj.response.GroupResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.junit.Before;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.opencommercesearch.junit.SearchTest;
import org.opencommercesearch.junit.runners.SearchJUnit4ClassRunner;
import org.opencommercesearch.repository.FacetProperty;
import org.opencommercesearch.repository.FacetRuleProperty;
import org.opencommercesearch.repository.FieldFacetProperty;
import org.opencommercesearch.repository.QueryFacetProperty;
import org.opencommercesearch.repository.RedirectRuleProperty;
import org.opencommercesearch.repository.RuleProperty;
import org.opencommercesearch.repository.SearchRepositoryItemDescriptor;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author rmerizalde
 */
@Category(IntegrationSearchTest.class)
@RunWith(SearchJUnit4ClassRunner.class)
public class AbstractSearchServerIntegrationTest {

    private static final String FIELD_FACET = "fieldFacet";
    private static final String QUERY_FACET = "queryFacet";

    public static final int ROWS = 20;
    public static final SimpleDateFormat isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    @Mock
    private Site site;

    @Mock
    private RepositoryItem catalog;

    @Mock
    private RepositoryItem redirectRule;

    @Mock
    private RepositoryItem expiredRule;

    @Mock
    private RepositoryItem futureRule;

    @Mock
    private RepositoryItem expiredRedirectRule;
    
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
        
        when(searchRepository.getItem("oldRule", SearchRepositoryItemDescriptor.RULE)).thenReturn(expiredRedirectRule);
        when(expiredRule.getPropertyValue(RedirectRuleProperty.URL)).thenReturn("/redirect");
        when(expiredRule.getPropertyValue(RedirectRuleProperty.RULE_TYPE)).thenReturn("redirectRule");
        
        when(searchRepository.getItem("futureRule", SearchRepositoryItemDescriptor.RULE)).thenReturn(futureRule);
        when(futureRule.getPropertyValue(RedirectRuleProperty.URL)).thenReturn("/redirect");
        when(futureRule.getPropertyValue(RedirectRuleProperty.RULE_TYPE)).thenReturn("redirectRule");
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
        query.setRows(ROWS);
        SearchResponse res = server.search(query, site);
        assertEquals("/redirect", res.getRedirectResponse());
    }

    @SearchTest(newInstance = true)
    public void testRulesLifetime(SearchServer server) throws SearchServerException, SolrServerException, IOException {
        EmbeddedSearchServer baseServer = (EmbeddedSearchServer) server;

        baseServer.updateCollection(baseServer.getRulesCollection(),  generateDatedRulesXml(), new Locale("en"));
        baseServer.setSearchRepository(searchRepository);

        SolrQuery query = new SolrQuery("redirect");
        query.setRows(ROWS);
        SearchResponse res = server.search(query, site);
        verify(redirectRule, times(1)).getPropertyValue(RedirectRuleProperty.URL);
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
        //for brand categories we shouldn't generate the category graph. Use the regular category facet
        assertNull(response.getCategoryGraph());
        Facet categoryFacet = response.getFacets().get(0);
        assertNotNull(categoryFacet);
        assertEquals("Kids' Clothing", categoryFacet.getFilters().get(0).getName());
        assertEquals("Shoes & Footwear", categoryFacet.getFilters().get(1).getName());
        validateCategoryPathNotInFacets(response);

    }

    @SearchTest(newInstance = true, productData = "/product_catalog/sandal.xml")
    public void testMinimumTerms(SearchServer server) throws SearchServerException {
    	
    	AbstractSearchServer abstractServer=  (AbstractSearchServer) server;
    	abstractServer.setMinimumMatch("2<-1 3<-2 5<80%");
    	SolrQuery query = new SolrQuery("Camp Sandal");
        query.setRows(ROWS);
        
        SearchResponse res = abstractServer.search(query, site);
        QueryResponse queryResponse = res.getQueryResponse();
        assertTrue(new Integer(1) <= queryResponse.getGroupResponse().getValues().get(0).getMatches());
    	
        //for 2 terms both should match
        query = new SolrQuery("Camp asdhja");
        query.setRows(ROWS);
        
        res = abstractServer.search(query, site);
        queryResponse = res.getQueryResponse();
        assertTrue(new Integer(0) == queryResponse.getGroupResponse().getValues().get(0).getMatches());
        
        //for 3 terms, atleast 2 should match
        query = new SolrQuery("Camp Sandal asdhja");
        query.setRows(ROWS);
        
        res = abstractServer.search(query, site);
        queryResponse = res.getQueryResponse();
        assertTrue(new Integer(1) <= queryResponse.getGroupResponse().getValues().get(0).getMatches());
        
        query = new SolrQuery("Camp asdhja sdjfhksd");
        query.setRows(ROWS);
        
        res = abstractServer.search(query, site);
        queryResponse = res.getQueryResponse();
        assertTrue(new Integer(0) == queryResponse.getGroupResponse().getValues().get(0).getMatches());
        
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
    
    @SearchTest(newInstance = true, productData = "/product_catalog/sandal.xml")
    public void testSortbySortField(SearchServer server) throws SearchServerException {
        AbstractSearchServer baseServer = (AbstractSearchServer) server;
        baseServer.setGroupSortingEnabled(true);
        SolrQuery query = new SolrQuery("Camp Sandal");
        query.setRows(ROWS);
        SearchResponse res = baseServer.search(query, site);
        QueryResponse queryResponse = res.getQueryResponse();
        assertEquals("TNF3137-FUPINYL-S3",(String) queryResponse.getGroupResponse().getValues().get(0).getValues().get(0).getResult().get(0).getFirstValue("id"));
        
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
                
        List<RepositoryItem> facetList = new ArrayList<RepositoryItem>();
        facetList.add(mockFacet("scale", "scale", FIELD_FACET, "UK Kids Footwear", null, false));
        facetList.add(mockFacet("brand", "brand", FIELD_FACET, "Fake Brand", null, false));
        mockFacetRuleResponse("facetRuleId", server, facetList);
        
        
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
        
    @SearchTest(newInstance = true, productData = "/product_catalog/sandal.xml", rulesData = "/rules/facetBlacklist.xml")
    public void testMultiSelectFacet(SearchServer server) throws SearchServerException, RepositoryException {
        // Scenario to validate multi-select facets filter results correctly
        
        // The test creates a global facet rule, in the rules collection, with the price and brand facets.
        // The brand facet will return one filter : the north face. 
        // The price facet will return two filters: $10-$20  and $20 And Up
        
        // We are going to apply first the brand filter 'the-north-face'
        // Then we apply the '$10-$20' and the '$20 And Up' filters. For each of them we verify they are selected
        
        // Finally we proceed to un-select them. First we remove the '$20 And Up', then the brand 'the-north-face'
        // and lastly we remove '$10-$20' and check no facet filters are selected.
                
        List<RepositoryItem> facetList = new ArrayList<RepositoryItem>();
        facetList.add(mockFacet("price", "salePriceUS", QUERY_FACET, null, Lists.newArrayList("[* TO 10]", "[10 TO 20]", "[20 TO *]"), true));
        facetList.add(mockFacet("brand", "brand", FIELD_FACET, null, null, true));
        mockFacetRuleResponse("facetRuleId", server, facetList);
        
        
        SolrQuery query = new SolrQuery("shoe");
        query.setRows(ROWS);
        SearchResponse response = server.search(query, site);
        
        Map<String, Facet> facetMap = Maps.newHashMap();
        validateRegularFacets(response, facetMap, null, null);
        
        //apply brand filter
        FilterQuery[] filterQueries = FilterQuery.parseFilterQueries(facetMap.get("brand").getFilters().get(0).getPath());
        response = server.search(query, site, filterQueries);
        validateRegularFacets(response, facetMap, Sets.newHashSet(0), null);

        //add filter by $10-$20
        filterQueries = FilterQuery.parseFilterQueries(facetMap.get("price").getFilters().get(0).getPath());
        response = server.search(query, site, filterQueries);        
        validateRegularFacets(response, facetMap, Sets.newHashSet(0), Sets.newHashSet(0));
        
        //add filter by $20 and up
        filterQueries = FilterQuery.parseFilterQueries(facetMap.get("price").getFilters().get(1).getPath());
        response = server.search(query, site, filterQueries);        
        validateRegularFacets(response, facetMap, Sets.newHashSet(0), Sets.newHashSet(0, 1));
        
        //remove filter by $20 and up
        filterQueries = FilterQuery.parseFilterQueries(facetMap.get("price").getFilters().get(1).getPath());
        response = server.search(query, site, filterQueries);        
        validateRegularFacets(response, facetMap, Sets.newHashSet(0), Sets.newHashSet(0));
        
        //remove brand filter
        filterQueries = FilterQuery.parseFilterQueries(facetMap.get("brand").getFilters().get(0).getPath());
        response = server.search(query, site, filterQueries);        
        validateRegularFacets(response, facetMap, null, Sets.newHashSet(0));
        
       //remove filter by $10-$20
        filterQueries = FilterQuery.parseFilterQueries(facetMap.get("price").getFilters().get(0).getPath());
        response = server.search(query, site, filterQueries);        
        validateRegularFacets(response, facetMap, null, null);
    }

    @SearchTest(newInstance = true, productData = "/product_catalog/sandal.xml", rulesData = "/rules/facetBlacklist.xml")
    public void testMultiSelectFacetRepeatedValues(SearchServer server) throws SearchServerException, RepositoryException {
        
        // Scenario to validate that multi select facets that have the same values but different fieldNames work ok
        
        // We are creating two facets: waterproof: yes,no and sunblock: yes,no
        // The test first selects waterproof=no and validates it's selected
        // Then we select sunblock=yes, do the corresponding validations and then proceed to deselect 
        // first waterproof and finally sunblock 
        
        
        List<RepositoryItem> facetList = new ArrayList<RepositoryItem>();
        facetList.add(mockFacet("waterproof", "attr_waterproof", FIELD_FACET, null, null, true));
        facetList.add(mockFacet("sunblock", "attr_sunblock", FIELD_FACET, null, null, true));
        
        mockFacetRuleResponse("facetRuleId", server, facetList);
        
        SolrQuery query = new SolrQuery("shoe");
        query.setRows(ROWS);
        SearchResponse response = server.search(query, site);
        Map<String, Facet> facetMap = Maps.newHashMap();
        validateLocalFacets(response, facetMap, null, null);

        //add filter by waterproof = no
        FilterQuery[] filterQueries = FilterQuery.parseFilterQueries(facetMap.get("waterproof").getFilters().get(0).getPath());
        response = server.search(query, site, filterQueries);
        validateLocalFacets(response, facetMap, Sets.newHashSet(0), null);
        
        //add filter by sunblock = yes
        filterQueries = FilterQuery.parseFilterQueries(facetMap.get("sunblock").getFilters().get(1).getPath());
        response = server.search(query, site, filterQueries);
        validateLocalFacets(response, facetMap, Sets.newHashSet(0), Sets.newHashSet(1));
        
        //remove filter by waterproof = no
        filterQueries = FilterQuery.parseFilterQueries(facetMap.get("waterproof").getFilters().get(0).getPath());
        response = server.search(query, site, filterQueries);
        validateLocalFacets(response, facetMap, null, Sets.newHashSet(1));
        
        //remove filter by sunblock = yes
        filterQueries = FilterQuery.parseFilterQueries(facetMap.get("sunblock").getFilters().get(1).getPath());
        response = server.search(query, site, filterQueries);
        validateLocalFacets(response, facetMap, null, null);
        
    }
    
    @SearchTest(newInstance = true, productData = "/product_catalog/sandal.xml")
    public void testGetFacet(SearchServer server) throws SearchServerException {
        Facet facet = server.getFacet(site, Locale.US,  "brandId", 100, -1, null);
        assertNotNull(facet);
        assertEquals(1, facet.getFilters().size());
        assertEquals("88", facet.getFilters().get(0).getName());   
        
        RepositoryItem catalogZ = mock(RepositoryItem.class);
        Site siteZ = mock(Site.class);
        when(siteZ.getPropertyValue("defaultCatalog")).thenReturn(catalogZ);
        when(catalogZ.getRepositoryId()).thenReturn("Zcatalog");
        facet = server.getFacet(siteZ, Locale.US, "brandId", 100, -1, null);
        assertNull(facet);
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
    
    private RepositoryItem mockFacet(String name, String field, String type, String blacklist, List<String> queries, Boolean isMultiselect){
        RepositoryItem facet = mock(RepositoryItem.class);
        when(facet.getRepositoryId()).thenReturn(name);
        when(facet.getPropertyValue(FacetProperty.TYPE)).thenReturn(type);
        when(facet.getPropertyValue(FacetProperty.NAME)).thenReturn(name);
        when(facet.getPropertyValue(FacetProperty.IS_MULTI_SELECT)).thenReturn(isMultiselect);
        when(facet.getPropertyValue(FieldFacetProperty.FIELD)).thenReturn(field);
        
        if(QUERY_FACET.equals(type)){
            when(facet.getPropertyValue(QueryFacetProperty.QUERIES)).thenReturn(queries);
        }
        
        if(StringUtils.isNotBlank(blacklist)) {
            Set<String> blackListSet = new HashSet<String>();
            blackListSet.add(blacklist);
            when(facet.getPropertyValue(FieldFacetProperty.BLACKLIST)).thenReturn(blackListSet);
        }
        return facet;
    }
    
    private void mockFacetRuleResponse(String facetRuleId, SearchServer server, List<RepositoryItem> facetList) throws RepositoryException{
        
        AbstractSearchServer baseServer = (AbstractSearchServer) server;
        baseServer.setSearchRepository(searchRepository);
        
        RepositoryItem facetRepoItem = mock(RepositoryItem.class);
        when(searchRepository.getItem(facetRuleId, SearchRepositoryItemDescriptor.RULE)).thenReturn(facetRepoItem);
        when(facetRepoItem.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn("facetRule");
        when(facetRepoItem.getRepositoryId()).thenReturn(facetRuleId);
        
        when(facetRepoItem.getPropertyValue(FacetRuleProperty.FACETS)).thenReturn(facetList);
    }
    
    public void validateRegularFacets(SearchResponse response, Map<String, Facet> facetMap, Set<Integer> brandSelectedIndex, Set<Integer> priceSelectedIndex) {
        facetMap.clear();
        for (Facet facet :response.getFacets()) {
            if (facet.getName().equals("price")) {
                assertEquals(facet.getFilters().size(), 2);
                assertEquals("$10-$20", facet.getFilters().get(0).getName());
                assertEquals("$20 And Up", facet.getFilters().get(1).getName());                 
                validateFacetFilter(priceSelectedIndex, facet);
                        
            }
            else if (facet.getName().equals("brand")) {
                assertEquals(facet.getFilters().size(), 1);
                assertEquals(facet.getFilters().get(0).getName(), "The North Face");                
                validateFacetFilter(brandSelectedIndex, facet);
            }
            facetMap.put(facet.getName(), facet);
        }
    }

    public void validateLocalFacets(SearchResponse response, Map<String, Facet> facetMap, 
            Set<Integer> waterproofSelectedIndex, Set<Integer> sunblockSelectedIndex) {
        facetMap.clear();
        int facetsProcees = 0;
        for (Facet facet :response.getFacets()) {
            
            if (facet.getName().equals("waterproof")) {
                assertEquals(facet.getFilters().size(), 2);
                assertEquals("no", facet.getFilters().get(0).getName());
                assertEquals("yes", facet.getFilters().get(1).getName()); 
                validateFacetFilter(waterproofSelectedIndex, facet);
                facetsProcees++;
            }
            else if (facet.getName().equals("sunblock")) { 
                assertEquals(facet.getFilters().size(), 2);
                assertEquals("no", facet.getFilters().get(0).getName());
                assertEquals("yes", facet.getFilters().get(1).getName()); 
                validateFacetFilter(sunblockSelectedIndex, facet);
                facetsProcees++;
            }
            facetMap.put(facet.getName(), facet);
        }
        assertEquals(2, facetsProcees);
    }
    
    public void validateFacetFilter(Set<Integer> selectedIndex, Facet facet) {
        if(selectedIndex == null) {
            selectedIndex = Sets.newHashSet();
        }
        
        Set<Integer> fullIndex = Sets.newHashSet();
        for(int i = 0; i < facet.getFilters().size(); i++){
            fullIndex.add(i);
        }
        
        for(int index : selectedIndex) {
            assertTrue(facet.getFilters().get(index).isSelected());
        }
        
        Set<Integer> unselectedIndex = Sets.difference(fullIndex, selectedIndex);
        for(int index : unselectedIndex) {
            assertFalse(facet.getFilters().get(index).isSelected());
        }
    }

    /**
     * Updates existing rules in the index with dates relative to the current time.
     * @return update XML message with the date updates.
     */
    private String generateDatedRulesXml() {
        StringBuffer update = new StringBuffer();
        Calendar cal = Calendar.getInstance();

        //Create updated dates for documents in the index (Solr4 rocks!)

        update.append("<add>");
        //Add an expired rule
        update.append("<doc>");
        update.append("<field name=\"id\">oldRule</field>");

        cal.setTime(new Date());
        cal.add(Calendar.DAY_OF_WEEK, -5);
        update.append("<field name=\"startDate\">" + getISODate(cal.getTime()) + "</field>");

        cal.setTime(new Date());
        cal.add(Calendar.DAY_OF_WEEK, -3);
        update.append("<field name=\"endDate\">" + getISODate(cal.getTime()) + "</field>");
        appendBoilerPlateFields(update);
        update.append("</doc>");

        //Add a valid rule now
        update.append("<doc>");
        update.append("<field name=\"id\">redirectRuleId</field>");

        cal.setTime(new Date());
        cal.add(Calendar.DAY_OF_WEEK, -2);
        update.append("<field name=\"startDate\">" + getISODate(cal.getTime()) + "</field>");

        cal.setTime(new Date());
        cal.add(Calendar.DAY_OF_WEEK, 2);
        update.append("<field name=\"endDate\">" + getISODate(cal.getTime()) + "</field>");
        appendBoilerPlateFields(update);
        update.append("</doc>");

        //One that hast't started yet
        update.append("<doc>");
        update.append("<field name=\"id\">futureRule</field>");
        
        cal.setTime(new Date());
        cal.add(Calendar.DAY_OF_WEEK, 5);
        update.append("<field name=\"startDate\">" + getISODate(cal.getTime()) + "</field>");

        cal.setTime(new Date());
        cal.add(Calendar.DAY_OF_WEEK, 6);
        update.append("<field name=\"endDate\">" + getISODate(cal.getTime()) + "</field>");
        appendBoilerPlateFields(update);
        update.append("</doc>");
        update.append("</add>");

        return update.toString();
    }

    private void appendBoilerPlateFields(StringBuffer update) {
        update.append("<field name=\"siteId\">__all__</field>");
        update.append("<field name=\"catalogId\">__all__</field>");
        update.append("<field name=\"category\">__all__</field>");
        update.append("<field name=\"target\">allpages</field>");
        update.append("<field name=\"query\">redirect</field>");
        update.append("<field name=\"brandId\">__all__</field>");
        update.append("<field name=\"subTarget\">__all__</field>");

    }

    /**
     * Just format a date to be ISO 8601 compliant so it can be indexed to Solr.
     */
    private String getISODate(Date date) {
        return isoDateFormat.format(date);
    }
}

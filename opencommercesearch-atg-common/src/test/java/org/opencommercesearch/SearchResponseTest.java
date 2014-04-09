package org.opencommercesearch;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class SearchResponseTest {

    @Mock
    QueryResponse queryResponse;
    
    @Mock
    RuleManager<?> ruleManager;
    
    @Mock
    FacetManager facetManager;
            
    @Before
    public void setUp() throws Exception {
        initMocks(this);
        when(ruleManager.getFacetManager()).thenReturn(facetManager);
        when(facetManager.facetFieldNames()).thenReturn(new HashSet<String>(Arrays.asList(new String[]{"size",})));
        Map<String, Integer> queryFacetMap = new HashMap<String, Integer>();
        queryFacetMap.put("size:170\\-175cm", 2);
        queryFacetMap.put("size:175\\-180cm", 1);
        queryFacetMap.put("size:180\\-185cm", 1);
        when(queryResponse.getFacetQuery()).thenReturn(queryFacetMap);
    }
    
    @Test
    public void testUnescapedQueryFacetCharacters() {
     FilterQuery[] filterQueries = new FilterQuery[] {new FilterQuery("size", "170\\-175cm")};
     when(facetManager.getFacetName("size")).thenReturn("size");
     SearchResponse reponse = new SearchResponse(null, queryResponse, ruleManager, filterQueries, null, null, false);
     List<Facet> facets = reponse.getFacets();
     assertEquals(facets.get(0).getName(), "size");
     assertEquals(facets.get(0).getSelectedFilters().size(), 1);
     assertEquals(facets.get(0).getSelectableFilters().size(), 3);
    }
}

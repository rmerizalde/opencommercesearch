package org.opencommercesearch;

import atg.multisite.Site;
import atg.repository.Repository;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import org.apache.solr.client.solrj.SolrQuery;
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
import org.opencommercesearch.repository.RedirectRuleProperty;
import org.opencommercesearch.repository.SearchRepositoryItemDescriptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author rmerizalde
 */
@Category(IntegrationSearchTest.class)
@RunWith(SearchJUnit4ClassRunner.class)
public class AbstractSearchServerIntegrationTest {

    @Mock
    private Site site;

    @Mock
    private RepositoryItem catalog;

    @Mock
    private RepositoryItem redirectRule;
    
    @Mock
    private Repository searchRepository;
    
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
        testSearchCategoryAux(server, "sneaker", "TNF3137");
    }
    
    @SearchTest(newInstance = true, rulesData = "/rules/redirect.xml")
    public void testSearchSedirect(SearchServer server) throws SearchServerException {
    	AbstractSearchServer baseServer = (AbstractSearchServer) server;
    	baseServer.setSearchRepository(searchRepository);
    	
    	SolrQuery query = new SolrQuery("redirect");
        SearchResponse res = server.search(query, site);
        assertEquals("/redirect", res.getRedirectResponse());
    }

    private void testSearchCategoryAux(SearchServer server, String term, String expectedProductId) throws SearchServerException {
        SolrQuery query = new SolrQuery(term);
        SearchResponse res = server.search(query, site);
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

    @SearchTest(newInstance = true)
    public void testDeleteByQuery(SearchServer server) throws SearchServerException {
        SolrQuery query = new SolrQuery("jacket");
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

}

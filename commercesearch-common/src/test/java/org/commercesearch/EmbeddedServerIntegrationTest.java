package org.commercesearch;

import atg.multisite.Site;
import atg.repository.RepositoryItem;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.*;
import org.apache.solr.common.SolrInputDocument;
import org.commercesearch.junit.SearchTest;
import org.commercesearch.junit.runners.SearchJUnit4ClassRunner;
import org.junit.Before;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.Arrays;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@Category(IntegrationSearchTest.class)
@RunWith(SearchJUnit4ClassRunner.class)
public class EmbeddedServerIntegrationTest {
    @Mock
    private Site site;

    @Mock
    private RepositoryItem catalog;

    @Before
    public void setUp() {
        initMocks(this);
        when(site.getRepositoryId()).thenReturn("mySite");
        when(site.getPropertyValue("defaultCatalog")).thenReturn(catalog);
        when(catalog.getRepositoryId()).thenReturn("myCatalog");
    }


    @SearchTest
    public void testPing(SearchServer server) throws SearchServerException {
        SolrPingResponse res = server.ping();
        assertThat(res.getQTime(), greaterThanOrEqualTo(0));
    }

    @SearchTest(newInstance = true)
    public void testUpdate(SearchServer server) throws SearchServerException {

        UpdateResponse res = addProduct(server, "PRD0000-SKU", "PRD0001");

        assertThat(res.getQTime(), greaterThanOrEqualTo(0));
    }

    @SearchTest(newInstance = true)
    public void testSearch(SearchServer server) throws SearchServerException {
        addProduct(server, "PRD0001-SKU", "PRD0001");

        SolrQuery query = new SolrQuery("face");
        SearchResponse res = server.search(query, site);
        QueryResponse queryResponse = res.getQueryResponse();
        GroupResponse groupResponse = queryResponse.getGroupResponse();

        for (GroupCommand command : groupResponse.getValues()) {
            assertEquals(new Integer(1), command.getNGroups());

            for (Group group : command.getValues()) {
                assertEquals("PRD0001", group.getGroupValue());
            }
        }
 
    }

    @SearchTest(newInstance = true)
    public void testEmptySearch(SearchServer server) throws SearchServerException {
        addProduct(server, "PRD0001-SKU", "PRD0001");

        SolrQuery query = new SolrQuery("bike");
        SearchResponse res = server.search(query, site);
        QueryResponse queryResponse = res.getQueryResponse();
        GroupResponse groupResponse = queryResponse.getGroupResponse();

        for (GroupCommand command : groupResponse.getValues()) {
            assertEquals(new Integer(0), command.getNGroups());
        }
    }

    private UpdateResponse addProduct(SearchServer server, String id, String productId) throws SearchServerException {
        SolrInputDocument doc = new SolrInputDocument();
        doc.setField("id", id);
        doc.setField("productId", productId);
        doc.setField("title", "The North Face T-Shirt");
        doc.setField("isToos", "false");
        doc.setField("category", "0.myCatalog");
        doc.setField("categoryId", "category0");
        doc.setField("listRank", "1");
        doc.setField("seoUrl", "/my-product");
        doc.setField("image", "my-product.jpg");
        doc.setField("brandId", "88");
        server.add(Arrays.asList(doc));

        return server.commit();
    }
}

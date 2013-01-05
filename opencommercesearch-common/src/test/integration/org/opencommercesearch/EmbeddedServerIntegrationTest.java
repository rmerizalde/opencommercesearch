package org.opencommercesearch;

import atg.multisite.Site;
import atg.repository.RepositoryItem;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.*;
import org.apache.solr.common.SolrInputDocument;
import org.opencommercesearch.junit.SearchTest;
import org.opencommercesearch.junit.runners.SearchJUnit4ClassRunner;
import org.junit.Before;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.Arrays;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
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
        when(catalog.getRepositoryId()).thenReturn("mycatalog");
    }

    @SearchTest
    public void testPing(SearchServer server) throws SearchServerException {
        SolrPingResponse res = server.ping();
        assertThat(res.getQTime(), greaterThanOrEqualTo(0));
    }

    /**
     * For this test case there's really no need to use another data file. The only
     * reason I'm using is for the sake of showing how it works.
     */
    @SearchTest(newInstance = true, productData = "/product_catalog/bike-products.xml")
    public void testUpdate(SearchServer server) throws SearchServerException {
        updateProduct(server);

        SolrQuery query = new SolrQuery("tallboy");
        SearchResponse res = server.search(query, site);
        QueryResponse queryResponse = res.getQueryResponse();
        GroupResponse groupResponse = queryResponse.getGroupResponse();

        for (GroupCommand command : groupResponse.getValues()) {
            assertEquals(new Integer(1), command.getNGroups());

            for (Group group : command.getValues()) {
                assertEquals(false, group.getResult().get(0).getFieldValue("isToos"));
            }
        }
    }

    private UpdateResponse updateProduct(SearchServer server) throws SearchServerException {
        SolrInputDocument doc = new SolrInputDocument();
        doc.setField("id", "SNZ0289-MATCAR-M");
        doc.setField("productId", "SNZ0289");
        doc.setField("title", "Santa Cruz Bicycles Tallboy LT Carbon - 2010");
        doc.setField("isToos", "false");
        doc.setField("category", "0.mycatalog");
        doc.setField("categoryId", "category0");
        doc.setField("listRank", "1");
        doc.setField("seoUrl", "/santa-cruz-bicycles-tallboy-lt-carbon");
        doc.setField("image", "MATCAR.jpg");
        doc.setField("brandId", "100000796");
        server.add(Arrays.asList(doc));

        return server.commit();
    }    

    @SearchTest
    public void testSearch(SearchServer server) throws SearchServerException {
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

    @SearchTest
    public void testEmptySearch(SearchServer server) throws SearchServerException {

        SolrQuery query = new SolrQuery("bike");
        SearchResponse res = server.search(query, site);
        QueryResponse queryResponse = res.getQueryResponse();
        GroupResponse groupResponse = queryResponse.getGroupResponse();

        for (GroupCommand command : groupResponse.getValues()) {
            assertEquals(new Integer(0), command.getNGroups());
        }
    }


}

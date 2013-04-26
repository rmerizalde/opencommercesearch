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

    public static final int ROWS = 20;

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

        query.setRows(ROWS);
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
        doc.setField("categoryPath", "mycatalog");
        doc.setField("ancestorCategoryId", "category0");
        doc.setField("categoryLeaves", "category 0");
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
        query.setRows(ROWS);
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
        query.setRows(ROWS);
        SearchResponse res = server.search(query, site);
        QueryResponse queryResponse = res.getQueryResponse();
        GroupResponse groupResponse = queryResponse.getGroupResponse();

        for (GroupCommand command : groupResponse.getValues()) {
            assertEquals(new Integer(0), command.getNGroups());
        }
    }

    @SearchTest(newInstance = true)
    public void testReloadCollection(SearchServer server) throws SearchServerException {
        EmbeddedSearchServer embeddedSearchServer = (EmbeddedSearchServer) server;
        // @TODO(rmerizalde) fix reload
        //embeddedSearchServer.reloadCollections();
    }


}

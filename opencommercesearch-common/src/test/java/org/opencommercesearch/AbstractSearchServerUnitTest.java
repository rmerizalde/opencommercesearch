package org.opencommercesearch;

import atg.multisite.Site;
import atg.repository.Repository;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryItemDescriptor;
import atg.repository.RepositoryView;
import atg.repository.rql.RqlStatement;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;
import org.apache.solr.common.params.SolrParams;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.opencommercesearch.repository.CategoryProperty;
import org.opencommercesearch.repository.RuleProperty;
import org.opencommercesearch.repository.SearchRepositoryItemDescriptor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockito.Mockito.when;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author rmerizalde
 */
public class AbstractSearchServerUnitTest {

    @Mock
    private Site site;

    @Mock
    private RepositoryItem catalog;

    @Mock
    private RepositoryItem category;

    @Mock
    private RepositoryItemDescriptor categoryItemDescriptor;

    @Mock
    private SolrQuery query;

    @Mock
    private Repository searchRepository;

    @Mock
    private RepositoryView repositoryView;

    @Mock
    private SolrServer catalogServer;

    @Mock
    private SolrServer rulesServer;

    @Mock
    private RqlStatement rulesRqlCount;

    @Mock
    private RqlStatement rulesRql;

    @Mock
    private QueryResponse catalogQueryResponse;

    @Mock
    private QueryResponse rulesQueryResponse;

    @Mock
    private RepositoryItem facetRule;

    @Mock
    private RepositoryItem boostRule;

    @Mock
    private RepositoryItem blockRule;

    @Mock
    private RepositoryItem redirectRule;

    private AbstractSearchServer server = new AbstractSearchServer() {

        @Override
        protected void exportSynonymList(RepositoryItem synonymList) throws SearchServerException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void reloadCollection(String collectionName) throws SearchServerException {
            throw new UnsupportedOperationException();
        }
    };

    @Before
    public void setup() throws Exception {
        initMocks(this);

        server.setCatalogCollection("catalog");
        server.setRulesCollection("rules");
        server.setCatalogSolrServer(catalogServer);
        server.setRulesSolrServer(rulesServer);
        server.setSearchRepository(searchRepository);
        server.setRuleCountRql(rulesRqlCount);
        server.setRuleRql(rulesRql);
        server.setLoggingInfo(false);
        server.setLoggingError(false);
        server.setLoggingError(false);
        server.setLoggingWarning(false);
        server.setLoggingTrace(false);

        // site
        when(site.getRepositoryId()).thenReturn("mysite");
        when(site.getPropertyValue("defaultCatalog")).thenReturn(catalog);

        //catalog
        when(catalog.getRepositoryId()).thenReturn("outdoor");

        // category
        when(category.getRepositoryId()).thenReturn("cat1");
        Set<String> searchTokens = new HashSet<String>();
        searchTokens.add("0.outdoor");
        searchTokens.add("1.outdoor.cat1");
        when(category.getPropertyValue(CategoryProperty.SEARCH_TOKENS)).thenReturn(searchTokens);
        when(category.getItemDescriptor()).thenReturn(categoryItemDescriptor);
        when(categoryItemDescriptor.getItemDescriptorName()).thenReturn("category");

        // solr servers
        when(query.getQuery()).thenReturn("my search term");
        when(catalogServer.query(any(SolrParams.class))).thenReturn(catalogQueryResponse);
        when(rulesServer.query(any(SolrParams.class))).thenReturn(rulesQueryResponse);

        // repository
        when(searchRepository.getView(SearchRepositoryItemDescriptor.RULE)).thenReturn(repositoryView);

        // rules
        Set<RepositoryItem> sites = new HashSet<RepositoryItem>();
        sites.add(site);

        Set<RepositoryItem> catalogs = new HashSet<RepositoryItem>();
        catalogs.add(catalog);

        Set<RepositoryItem> categories = new HashSet<RepositoryItem>();
        categories.add(category);

        when(facetRule.getRepositoryId()).thenReturn("faceRule");
        when(facetRule.getPropertyValue(RuleProperty.QUERY)).thenReturn("*");
        when(facetRule.getPropertyValue(RuleProperty.SITES)).thenReturn(sites);
        when(facetRule.getPropertyValue(RuleProperty.CATALOGS)).thenReturn(catalogs);
        when(facetRule.getPropertyValue(RuleProperty.CATEGORIES)).thenReturn(categories);
        when(boostRule.getRepositoryId()).thenReturn("boostRule");
        when(blockRule.getRepositoryId()).thenReturn("blockRule");
        when(redirectRule.getRepositoryId()).thenReturn("redirectRule");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testSearchNoSite() throws SearchServerException {
        Site nullSite = null;
        server.search(query, nullSite);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testSearchNoCatalog() throws SearchServerException {
        when(site.getPropertyValue("defaultCatalog")).thenReturn(null);
        server.search(query, site);
    }

    @Test
    public void testIndexRulesNoRules() throws Exception {
        ArgumentCaptor<UpdateRequest> argument = ArgumentCaptor.forClass(UpdateRequest.class);
        when(rulesRqlCount.executeCountQuery(repositoryView, null)).thenReturn(0);

        server.indexRules();

        verify(rulesServer).request(argument.capture());
        assertNotNull(argument.getValue().getDeleteQuery());
        assertEquals(1, argument.getValue().getDeleteQuery().size());
        assertEquals("*:*", argument.getValue().getDeleteQuery().get(0));
        verify(rulesServer).commit();
    }

    @Test
    public void testIndexRules() throws Exception {
        ArgumentCaptor<UpdateRequest> argument = ArgumentCaptor.forClass(UpdateRequest.class);
        when(rulesRqlCount.executeCountQuery(repositoryView, null)).thenReturn(4);
        when(rulesRql.executeQueryUncached(eq(repositoryView), (Object[]) anyObject())).thenReturn(new RepositoryItem[]{
                redirectRule, boostRule, blockRule, facetRule
        }).thenReturn(null);

        server.indexRules();

        verify(rulesServer, times(2)).request(argument.capture());
        List<SolrInputDocument> documents = argument.getValue().getDocuments();
        assertNotNull(documents);
        assertEquals(4, documents.size());
        List<String> ruleIds = new ArrayList();
        assertEquals("/update", argument.getValue().getPath());

        for (SolrInputDocument document : documents) {
            ruleIds.add((String) document.getFieldValue("id"));
        }
        assertThat(ruleIds, containsInAnyOrder(redirectRule.getRepositoryId(), facetRule.getRepositoryId(),
                blockRule.getRepositoryId(), boostRule.getRepositoryId()));

        verify(rulesServer).commit();
    }
}

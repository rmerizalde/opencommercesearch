package org.opencommercesearch;

import atg.multisite.Site;
import atg.repository.Repository;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryItemDescriptor;
import atg.repository.RepositoryView;
import atg.repository.rql.RqlStatement;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.SolrParams;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.opencommercesearch.repository.CategoryProperty;
import org.opencommercesearch.repository.RuleProperty;
import org.opencommercesearch.repository.SearchRepositoryItemDescriptor;
import org.opencommercesearch.repository.SynonymListProperty;

import java.io.IOException;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

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

    @Mock
    private RqlStatement synonymsRql;

    @Mock
    private RepositoryItem synonymList;

    @Mock
    private AbstractSearchServer dummySearchServer;

    private AbstractSearchServer server = new AbstractSearchServer() {

        @Override
        protected void exportSynonymList(RepositoryItem synonymList, Locale locale) throws SearchServerException {
            dummySearchServer.exportSynonymList(synonymList, locale);
        }

        @Override
        public void reloadCollection(String collectionName, Locale locale) throws SearchServerException {
            dummySearchServer.reloadCollection(collectionName, locale);
        }

        @Override
        public void logInfo(String s, Throwable t) {
        }
    };

    private Locale getLocale() {
        return Locale.ENGLISH;
    }

    @Before
    public void setup() throws Exception {
        initMocks(this);

        server.setCatalogCollection("catalog");
        server.setRulesCollection("rules");
        server.setCatalogSolrServer(catalogServer, getLocale());
        server.setRulesSolrServer(rulesServer, getLocale());
        server.setSearchRepository(searchRepository);
        server.setRuleCountRql(rulesRqlCount);
        server.setRuleRql(rulesRql);
        server.setSynonymRql(synonymsRql);
        server.setLoggingInfo(true);
        server.setLoggingError(true);
        server.setLoggingError(true);
        server.setLoggingWarning(true);
        server.setLoggingTrace(true);

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
        when(searchRepository.getRepositoryName()).thenReturn("SearchRepository");
        when(searchRepository.getView(SearchRepositoryItemDescriptor.RULE)).thenReturn(repositoryView);
        when(searchRepository.getView(SearchRepositoryItemDescriptor.SYNONYM_LIST)).thenReturn(repositoryView);
        when(synonymsRql.executeQuery(repositoryView,  null)).thenReturn(new RepositoryItem[]{synonymList});

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
        when(rulesRqlCount.executeCountQuery(repositoryView, null)).thenReturn(4);
        when(rulesRql.executeQueryUncached(eq(repositoryView), (Object[]) anyObject())).thenReturn(new RepositoryItem[]{
                redirectRule, boostRule, blockRule, facetRule
        }).thenReturn(null);

        server.indexRules();

        verifyIndexedRules(4, redirectRule.getRepositoryId(), facetRule.getRepositoryId(),
            blockRule.getRepositoryId(), boostRule.getRepositoryId());
    }

    private void verifyIndexedRules(int count, String... expectedRuleIds) throws SolrServerException, IOException {

        ArgumentCaptor<UpdateRequest> argument = ArgumentCaptor.forClass(UpdateRequest.class);

        verify(rulesServer, times(2)).request(argument.capture());

        List<SolrInputDocument> documents = argument.getValue().getDocuments();
        assertNotNull(documents);
        assertEquals(count, documents.size());
        List<String> ruleIds = new ArrayList();
        assertEquals("/update", argument.getValue().getPath());

        for (SolrInputDocument document : documents) {
            ruleIds.add((String) document.getFieldValue("id"));
        }
        assertThat(ruleIds, containsInAnyOrder(expectedRuleIds));

        verify(rulesServer).commit();
    }

    @Test
    public void testItemChangedSynonym() throws Exception {
        Set<String> itemDescriptorNames = new HashSet<String>();
        itemDescriptorNames.add(SearchRepositoryItemDescriptor.SYNONYM);
        server.onRepositoryItemChanged("org.opencommercesearch.SearchRepository", itemDescriptorNames);
        verify(dummySearchServer).exportSynonymList(synonymList, getLocale());
        verify(dummySearchServer).reloadCollection(server.getCatalogCollection(), getLocale());
        verify(dummySearchServer).reloadCollection(server.getRulesCollection(), getLocale());
    }

    @Test
    public void testItemChangedSynonymList() throws Exception {
        Set<String> itemDescriptorNames = new HashSet<String>();
        itemDescriptorNames.add(SearchRepositoryItemDescriptor.SYNONYM_LIST);
        server.onRepositoryItemChanged("org.opencommercesearch.SearchRepository", itemDescriptorNames);
        verify(dummySearchServer, times(1)).exportSynonymList(synonymList, getLocale());
        verify(dummySearchServer, times(1)).reloadCollection(server.getCatalogCollection(), getLocale());
        verify(dummySearchServer, times(1)).reloadCollection(server.getRulesCollection(), getLocale());
    }

    @Test
    public void testItemChangedBoostRule() throws Exception {
        when(rulesRqlCount.executeCountQuery(repositoryView, null)).thenReturn(1);
        when(rulesRql.executeQueryUncached(eq(repositoryView), (Object[]) anyObject()))
                .thenReturn(new RepositoryItem[]{boostRule})
                .thenReturn(null);

        Set<String> itemDescriptorNames = new HashSet<String>();
        itemDescriptorNames.add(SearchRepositoryItemDescriptor.BOOST_RULE);
        server.onRepositoryItemChanged("org.opencommercesearch.SearchRepository", itemDescriptorNames);
        verify(dummySearchServer, times(0)).exportSynonymList(synonymList, getLocale());
        verifyIndexedRules(1, boostRule.getRepositoryId());
    }

    @Test
    public void testItemChangedBlockRule() throws Exception {
        when(rulesRqlCount.executeCountQuery(repositoryView, null)).thenReturn(1);
        when(rulesRql.executeQueryUncached(eq(repositoryView), (Object[]) anyObject()))
                .thenReturn(new RepositoryItem[]{blockRule})
                .thenReturn(null);

        Set<String> itemDescriptorNames = new HashSet<String>();
        itemDescriptorNames.add(SearchRepositoryItemDescriptor.BLOCK_RULE);
        server.onRepositoryItemChanged("org.opencommercesearch.SearchRepository", itemDescriptorNames);
        verify(dummySearchServer, times(0)).exportSynonymList(synonymList, getLocale());
        verifyIndexedRules(1, blockRule.getRepositoryId());
    }

    @Test
    public void testItemChangeFacetRule() throws Exception {
        when(rulesRqlCount.executeCountQuery(repositoryView, null)).thenReturn(1);
        when(rulesRql.executeQueryUncached(eq(repositoryView), (Object[]) anyObject()))
                .thenReturn(new RepositoryItem[]{facetRule})
                .thenReturn(null);

        Set<String> itemDescriptorNames = new HashSet<String>();
        itemDescriptorNames.add(SearchRepositoryItemDescriptor.FACET_RULE);
        server.onRepositoryItemChanged("org.opencommercesearch.SearchRepository", itemDescriptorNames);
        verify(dummySearchServer, times(0)).exportSynonymList(synonymList, getLocale());
        verifyIndexedRules(1, facetRule.getRepositoryId());
    }

    @Test
    public void testItemChangedRedirectRule() throws Exception {
        when(rulesRqlCount.executeCountQuery(repositoryView, null)).thenReturn(1);
        when(rulesRql.executeQueryUncached(eq(repositoryView), (Object[]) anyObject()))
                .thenReturn(new RepositoryItem[]{redirectRule})
                .thenReturn(null);

        Set<String> itemDescriptorNames = new HashSet<String>();
        itemDescriptorNames.add(SearchRepositoryItemDescriptor.REDIRECT_RULE);
        server.onRepositoryItemChanged("org.opencommercesearch.SearchRepository", itemDescriptorNames);
        verify(dummySearchServer, times(0)).exportSynonymList(synonymList, getLocale());
        verifyIndexedRules(1, redirectRule.getRepositoryId());
    }
}

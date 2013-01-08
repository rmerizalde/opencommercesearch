package org.opencommercesearch;

import atg.repository.Repository;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryView;
import atg.repository.rql.RqlStatement;
import com.google.common.collect.Sets;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.solr.client.solrj.ResponseParser;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.client.solrj.impl.LBHttpSolrServer;
import org.apache.solr.common.cloud.*;
import org.apache.solr.common.util.NamedList;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.opencommercesearch.repository.SearchRepositoryItemDescriptor;
import org.opencommercesearch.repository.SynonymListProperty;
import org.opencommercesearch.repository.SynonymProperty;

import java.io.InputStream;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class CloudSearchServerUnitTest {

    @Mock
    private SolrZkClient zkClient;

    @Mock
    private ZkStateReader zkStatereader;

    @Mock
    private ClusterState clusterState;

    @Mock
    private Slice slice1;

    @Mock
    private Slice slice2;

    @Mock
    private Replica replica1;

    @Mock
    private Replica replica2;

    @Mock
    private CloudSolrServer catalogSolrServer;

    @Mock
    private CloudSolrServer rulesSolrServer;

    @Mock
    private LBHttpSolrServer lbHttpSolrServer;

    @Mock
    private HttpClient httpClient;

    @Mock
    private StatusLine httpStatusLine;

    @Mock
    private HttpResponse httpResponse;

    @Mock
    private HttpEntity httpEntity;

    @Mock
    private InputStream httpInputStream;

    @Mock
    private ResponseParser responseParser;

    @Mock
    private Repository searchRepository;

    @Mock
    private RepositoryView repositoryView;

    @Mock
    private RqlStatement synonymsRql;

    @Mock
    private SolrZkClient solrZkClient;

    private CloudSearchServer cloudSearchServer;
    
    @Before
    public void setUp() throws Exception {
        initMocks(this);
        cloudSearchServer = new CloudSearchServer() {

            @Override
            public void logInfo(String s, Throwable t) {
            }
        };
        cloudSearchServer.setRulesCollection("rules");
        cloudSearchServer.setCatalogCollection("catalog");
        cloudSearchServer.setCatalogSolrServer(catalogSolrServer);
        cloudSearchServer.setRulesSolrServer(rulesSolrServer);
        cloudSearchServer.setLoggingInfo(true);
        cloudSearchServer.setLoggingError(true);
        cloudSearchServer.setLoggingError(true);
        cloudSearchServer.setLoggingWarning(true);
        cloudSearchServer.setLoggingTrace(true);
        cloudSearchServer.setResponseParser(responseParser);
        cloudSearchServer.setSearchRepository(searchRepository);
        cloudSearchServer.setSynonymRql(synonymsRql);

        initZkMocks();
        initHttpMocks();

        //when(responseParser.)
        when(searchRepository.getView(SearchRepositoryItemDescriptor.SYNONYM_LIST)).thenReturn(repositoryView);
        when(responseParser.processResponse(eq(httpInputStream), anyString())).thenReturn(new NamedList<Object>());
    }

    private void initZkMocks() throws KeeperException, InterruptedException {
        when(zkClient.exists(anyString(), anyBoolean())).thenReturn(false);
        when(zkStatereader.getClusterState()).thenReturn(clusterState);
        Set<String> liveNodes = new HashSet<String>();
        liveNodes.add("nodeName1");
        liveNodes.add("nodeName2");
        when(clusterState.getLiveNodes()).thenReturn(liveNodes);

        Map<String, Slice> slices = new HashMap<String, Slice>();
        slices.put("slice1", slice1);
        slices.put("slice2", slice2);
        when(clusterState.getSlices(cloudSearchServer.getRulesCollection())).thenReturn(slices);
        when(clusterState.getSlices(cloudSearchServer.getCatalogCollection())).thenReturn(slices);

        Collection<Replica> replicas = Arrays.asList(replica1, replica2);
        when(slice1.getReplicas()).thenReturn(replicas);

        when(replica1.getStr(ZkStateReader.NODE_NAME_PROP)).thenReturn("nodeName1");
        when(replica2.getStr(ZkStateReader.NODE_NAME_PROP)).thenReturn("nodeName2");
        when(replica1.getStr(ZkStateReader.STATE_PROP)).thenReturn(ZkStateReader.ACTIVE);
        when(replica2.getStr(ZkStateReader.STATE_PROP)).thenReturn(ZkStateReader.DOWN);
        when(replica1.getStr(ZkStateReader.BASE_URL_PROP)).thenReturn("http://node1.opencommercesearch.org");
        when(replica2.getStr(ZkStateReader.BASE_URL_PROP)).thenReturn("http://node2.opencommercesearch.org");
        when(replica1.getStr(ZkStateReader.CORE_NAME_PROP)).thenReturn("mycore");
        when(replica2.getStr(ZkStateReader.CORE_NAME_PROP)).thenReturn("mycore");

        when(catalogSolrServer.getZkStateReader()).thenReturn(zkStatereader);
        when(catalogSolrServer.getLbServer()).thenReturn(lbHttpSolrServer);
        when(rulesSolrServer.getZkStateReader()).thenReturn(zkStatereader);
        when(rulesSolrServer.getLbServer()).thenReturn(lbHttpSolrServer);
        when(lbHttpSolrServer.getHttpClient()).thenReturn(httpClient);
    }

    private void initHttpMocks() throws Exception {
        when(httpClient.execute(any(HttpRequestBase.class))).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(httpStatusLine);
        when(httpStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
        when(httpResponse.getEntity()).thenReturn(httpEntity);
        when(httpEntity.getContent()).thenReturn(httpInputStream);
    }

    @Test
    public void testExportSynonymListNewFile() throws Exception {
        
        RepositoryItem synonymList = initExportSynonyms(zkClient);
        when(synonymsRql.executeQuery(repositoryView, null)).thenReturn(new RepositoryItem[]{synonymList});

        cloudSearchServer.exportSynonyms();
        
        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(zkClient, times(2)).makePath(pathCaptor.capture(), dataCaptor.capture(), any(CreateMode.class), eq(true));
        
        verifySynonymExport(dataCaptor, pathCaptor);
    }
    
    @Test
    public void testExportSynonymListExistingFile() throws Exception {

        RepositoryItem synonymList = initExportSynonyms(zkClient);
        when(synonymsRql.executeQuery(repositoryView, null)).thenReturn(new RepositoryItem[]{synonymList});

        when(zkClient.exists(anyString(), anyBoolean())).thenReturn(true);

        cloudSearchServer.exportSynonyms();
        
        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(zkClient, times(2)).setData(pathCaptor.capture(), dataCaptor.capture(), eq(true));
        
        verifySynonymExport(dataCaptor, pathCaptor);
    }

    private void verifySynonymExport(ArgumentCaptor<byte[]> dataCaptor, ArgumentCaptor<String> pathCaptor) {
        assertNotNull(dataCaptor.getAllValues());
        byte[] capturedData = dataCaptor.getAllValues().get(0);
        String capturedDataStr = new String(capturedData);
        
        assertTrue(capturedDataStr.contains("synonym1 > mapping1"));
        assertTrue(capturedDataStr.contains("synonym2 > mapping2"));
        
        assertTrue(pathCaptor.getAllValues().get(0).contains("/configs/catalogCollection/synonyms/synonymlist.txt"));
        assertTrue(pathCaptor.getAllValues().get(1).contains("/configs/ruleCollection/synonyms/synonymlist.txt"));
    }

    private RepositoryItem initExportSynonyms(SolrZkClient zkClient) throws SearchServerException {
        cloudSearchServer.setSolrZkClient(zkClient);
        
        RepositoryItem synonymList = mock(RepositoryItem.class);
        when(synonymList.getItemDisplayName()).thenReturn("synonymList");

        Set<RepositoryItem> mappings = 
                Sets.newHashSet(mockMapping("synonym1 > mapping1"), 
                                mockMapping("synonym2 > mapping2"));
        when(synonymList.getPropertyValue(SynonymListProperty.MAPPINGS)).thenReturn(mappings);
        
        cloudSearchServer.setCatalogCollection("catalogCollection");
        cloudSearchServer.setRulesCollection("ruleCollection");
        
        return synonymList;
    }
    
    private RepositoryItem mockMapping(String mappingStr){
        RepositoryItem mapping = mock(RepositoryItem.class);
        when(mapping.getPropertyValue(SynonymProperty.MAPPING)).thenReturn(mappingStr);
        return mapping;
                
    }

    @Test
    public void testReloadCollections() throws Exception {
       cloudSearchServer.reloadCollections();

        ArgumentCaptor<HttpRequestBase> argument = ArgumentCaptor.forClass(HttpRequestBase.class);
        verify(httpClient, times(2)).execute(argument.capture());
        List<String> requestUrls = new ArrayList<String>(argument.getAllValues().size());
        for (HttpRequestBase request : argument.getAllValues()) {
            requestUrls.add(request.getURI().toString());
        }
        assertThat(requestUrls, containsInAnyOrder(
            "http://node1.opencommercesearch.org/mycore/admin/cores?action=RELOAD&core=" + cloudSearchServer.getCatalogCollection(),
            "http://node1.opencommercesearch.org/mycore/admin/cores?action=RELOAD&core=" + cloudSearchServer.getRulesCollection()
        ));
    }

    @Test
    public void testReloadCollectionsNoLiveNodes() throws Exception {
        when(clusterState.getLiveNodes()).thenReturn(null);
        cloudSearchServer.reloadCollections();

        verifyZeroInteractions(httpClient);
    }

    @Test
    public void testInitSolrServer() throws Exception {
        CloudSearchServer server = new CloudSearchServer();

        server.setCatalogCollection(cloudSearchServer.getCatalogCollection());
        server.setRulesCollection(cloudSearchServer.getRulesCollection());

        assertNull(server.getCatalogSolrServer());
        assertNull(server.getRulesSolrServer());

        server.initSolrServer();

        assertNotNull(server.getCatalogSolrServer());
        assertNotNull(server.getRulesSolrServer());
    }

}

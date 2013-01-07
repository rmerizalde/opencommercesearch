package org.opencommercesearch;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Set;

import org.apache.solr.common.cloud.SolrZkClient;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opencommercesearch.repository.SynonymListProperty;
import org.opencommercesearch.repository.SynonymProperty;

import com.google.common.collect.Sets;

import atg.repository.RepositoryItem;

public class CloudSearchServerUnitTest {

    CloudSearchServer cloudSearchServer;
    
    @Before
    public void setUp() throws Exception {
        cloudSearchServer = new CloudSearchServer();
        cloudSearchServer.setLoggingInfo(false);
        cloudSearchServer.setLoggingError(false);
        cloudSearchServer.setLoggingError(false);
        cloudSearchServer.setLoggingWarning(false);
        cloudSearchServer.setLoggingTrace(false);
    }

    @Test
    public void testExportSynonymList() throws SearchServerException, KeeperException, InterruptedException {
        
        SolrZkClient zkClient = mock(SolrZkClient.class);
        RepositoryItem synonymList = initExportSynonyms(zkClient);
        when(zkClient.exists(anyString(), anyBoolean())).thenReturn(false);
        
        cloudSearchServer.exportSynonymList(synonymList);
        
        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(zkClient, times(2)).makePath(pathCaptor.capture(), dataCaptor.capture(), any(CreateMode.class), eq(true));
        
        assertNotNull(dataCaptor.getAllValues());
        byte[] capturedData = dataCaptor.getAllValues().get(0);
        String capturedDataStr = new String(capturedData);
        
        assertTrue(capturedDataStr.contains("synonym1 > mapping1"));
        assertTrue(capturedDataStr.contains("synonym2 > mapping2"));
        
        String path = pathCaptor.getAllValues().get(0);
        assertTrue(path.contains("synonymlist"));
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

}

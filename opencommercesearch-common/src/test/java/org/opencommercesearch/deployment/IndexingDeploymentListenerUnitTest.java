package org.opencommercesearch.deployment;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opencommercesearch.SearchServer;
import org.opencommercesearch.SearchServerException;
import org.opencommercesearch.SearchServerException.Code;

import atg.deployment.common.Status;
import atg.deployment.common.event.DeploymentEvent;
import atg.repository.RepositoryException;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class IndexingDeploymentListenerUnitTest {

    IndexingDeploymentListener indexingDeploymentListener = new IndexingDeploymentListener();
    
    @Mock
    SearchServer searchServer;
    
    @Before
    public void setUp() throws Exception {
        initMocks(this);
        indexingDeploymentListener.setSearchServer(searchServer);
        indexingDeploymentListener.setTriggerStatus(Status.stateToString(1));
        indexingDeploymentListener.setTriggerItemDescriptorNames(Lists.newArrayList("searchRepo:triggerItemDescriptor1", "searchRepo:triggerItemDescriptor2"));
        indexingDeploymentListener.setLoggingDebug(false);
        indexingDeploymentListener.setLoggingError(false);
        indexingDeploymentListener.setLoggingInfo(false);
        indexingDeploymentListener.setLoggingTrace(false);
        indexingDeploymentListener.setLoggingWarning(false);
    }

    @Test
    public void testDeploymentEvent() throws RepositoryException, SearchServerException {
        
        DeploymentEvent event = mock(DeploymentEvent.class);
        when(event.getNewState()).thenReturn(1);
        
        Map<String, Set<String>> affectedItemTypes = Maps.newHashMap();
        when(event.getAffectedItemTypes()).thenReturn(affectedItemTypes);
        HashSet<String> affectedItemSet = Sets.newHashSet("triggerItemDescriptor1", "triggerItemDescriptor2");
        affectedItemTypes.put("searchRepo", affectedItemSet);
        
        indexingDeploymentListener.deploymentEvent(event );
        
        verify(searchServer).onRepositoryItemChanged("searchRepo", affectedItemSet);
    }


    @Test
    public void testDeploymentEventAtLeastOneMatch() throws RepositoryException, SearchServerException {
        
        DeploymentEvent event = mock(DeploymentEvent.class);
        when(event.getNewState()).thenReturn(1);
        
        Map<String, Set<String>> affectedItemTypes = Maps.newHashMap();
        when(event.getAffectedItemTypes()).thenReturn(affectedItemTypes);
        HashSet<String> affectedItemSet = Sets.newHashSet("unknownItemDescriptor", "triggerItemDescriptor2");
        affectedItemTypes.put("searchRepo", affectedItemSet);
        
        indexingDeploymentListener.deploymentEvent(event );
        
        verify(searchServer).onRepositoryItemChanged("searchRepo", affectedItemSet);
    }
    
    @Test
    public void testDeploymentEventNoMatch() throws RepositoryException, SearchServerException {
        
        DeploymentEvent event = mock(DeploymentEvent.class);
        when(event.getNewState()).thenReturn(1);
        
        Map<String, Set<String>> affectedItemTypes = Maps.newHashMap();
        when(event.getAffectedItemTypes()).thenReturn(affectedItemTypes);
        HashSet<String> affectedItemSet = Sets.newHashSet("unknownItemDescriptor");
        affectedItemTypes.put("searchRepo", affectedItemSet);
        
        indexingDeploymentListener.deploymentEvent(event );
        
        verify(searchServer, never()).onRepositoryItemChanged(anyString(), any(Set.class));
    }
    
    @Test
    public void testNotifyItemChangeExceptions1() {
        try {
            doThrow(new RepositoryException()).when(searchServer).onRepositoryItemChanged(anyString(), any(Set.class));
            indexingDeploymentListener.notifyItemChange("repo", Sets.newHashSet("itemDescriptor"));
        } catch (SearchServerException e) {
            fail("The exception should be captured by the class");
        } catch (RepositoryException e) {
            fail("The exception should be captured by the class");
        }
    }
    
    @Test
    public void testNotifyItemChangeExceptions2() {
        try {
            doThrow(SearchServerException.create(Code.ANALYSIS_EXCEPTION)).when(searchServer).onRepositoryItemChanged(anyString(), any(Set.class));
            indexingDeploymentListener.notifyItemChange("repo", Sets.newHashSet("itemDescriptor"));
        } catch (SearchServerException e) {
            fail("The exception should be captured by the class");
        } catch (RepositoryException e) {
            fail("The exception should be captured by the class");
        }
    }
    
}

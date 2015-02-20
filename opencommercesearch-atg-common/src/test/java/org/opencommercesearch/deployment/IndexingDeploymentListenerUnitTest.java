package org.opencommercesearch.deployment;

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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opencommercesearch.SearchServer;
import org.opencommercesearch.SearchServerException;
import org.opencommercesearch.SearchServerException.Code;

import atg.deployment.common.Status;
import atg.deployment.common.event.DeploymentEvent;
import atg.repository.RepositoryException;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.opencommercesearch.feed.CategoryFeed;
import org.opencommercesearch.feed.FacetFeed;
import org.opencommercesearch.feed.RuleFeed;

public class IndexingDeploymentListenerUnitTest {

    IndexingDeploymentListener indexingDeploymentListener = new IndexingDeploymentListener();
    
    @Mock
    SearchServer searchServer;

    @Mock
    RuleFeed ruleFeed;

    @Mock
    FacetFeed facetFeed;

    @Mock
    CategoryFeed categoryFeed;
    
    @Mock
    ExecutorService executor;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        indexingDeploymentListener.setEnabled(true);
        indexingDeploymentListener.setSearchServer(searchServer);
        indexingDeploymentListener.setTriggerStatus(Status.stateToString(1));
        indexingDeploymentListener.setTriggerItemDescriptorNames(Lists.newArrayList("searchRepo:triggerItemDescriptor1", "searchRepo:triggerItemDescriptor2"));
        indexingDeploymentListener.setFacetsTriggerItemDescriptorNames(Lists.newArrayList("searchRepo:triggerItemDescriptor3", "searchRepo:triggerItemDescriptor4"));
        indexingDeploymentListener.setRulesTriggerItemDescriptorNames(Lists.newArrayList("searchRepo:triggerItemDescriptor5", "searchRepo:triggerItemDescriptor6"));
        indexingDeploymentListener.setCategoryTriggerItemDescriptorNames(Lists.newArrayList("productRepo:triggerItemDescriptor7", "productRepo:triggerItemDescriptor8"));
        indexingDeploymentListener.setLoggingDebug(false);
        indexingDeploymentListener.setLoggingError(false);
        indexingDeploymentListener.setLoggingInfo(false);
        indexingDeploymentListener.setLoggingTrace(false);
        indexingDeploymentListener.setLoggingWarning(false);
        indexingDeploymentListener.setRuleFeed(ruleFeed);
        indexingDeploymentListener.setFacetFeed(facetFeed);
        indexingDeploymentListener.setCategoryFeed(categoryFeed);
        indexingDeploymentListener.doStartService();
    }

    @After
    public void tearDown() throws Exception {
        indexingDeploymentListener.doStopService();
    }

    @Test
    public void testDeploymentEvent() throws RepositoryException, SearchServerException, IOException {
        DeploymentEvent event = mock(DeploymentEvent.class);
        when(event.getNewState()).thenReturn(1);
        
        Map<String, Set<String>> affectedItemTypes = Maps.newHashMap();
        when(event.getAffectedItemTypes()).thenReturn(affectedItemTypes);
        HashSet<String> affectedItemSet = Sets.newHashSet("triggerItemDescriptor1", "triggerItemDescriptor2");
        affectedItemTypes.put("searchRepo", affectedItemSet);
        
        indexingDeploymentListener.deploymentEvent(event);
        
        verify(searchServer).onRepositoryItemChanged("searchRepo", affectedItemSet);
        verify(facetFeed, times(0)).startFeed();
        verify(ruleFeed, times(0)).startFeed();
        verifyZeroInteractions(ruleFeed);
        verify(categoryFeed, times(0)).startFeed();
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

    @Test
    public void testFacetsDeploymentEvent() throws RepositoryException, SearchServerException, IOException {
        DeploymentEvent event = mock(DeploymentEvent.class);
        when(event.getNewState()).thenReturn(1);

        Map<String, Set<String>> affectedItemTypes = Maps.newHashMap();
        when(event.getAffectedItemTypes()).thenReturn(affectedItemTypes);
        HashSet<String> affectedItemSet = Sets.newHashSet("triggerItemDescriptor3", "triggerItemDescriptor4");
        affectedItemTypes.put("searchRepo", affectedItemSet);

        indexingDeploymentListener.deploymentEvent(event );

        verify(facetFeed).startFeed();
        verify(ruleFeed, times(0)).startFeed();
        verifyZeroInteractions(ruleFeed);
        verify(categoryFeed, times(0)).startFeed();
        verify(searchServer, times(0)).onRepositoryItemChanged("searchRepo", affectedItemSet);
    }

    @Test
    public void testRulesDeploymentEvent() throws RepositoryException, SearchServerException, IOException, InterruptedException {
    	final CountDownLatch endGate = new CountDownLatch(1);
        DeploymentEvent event = mock(DeploymentEvent.class);
        when(event.getNewState()).thenReturn(1);

        Map<String, Set<String>> affectedItemTypes = Maps.newHashMap();
        when(event.getAffectedItemTypes()).thenReturn(affectedItemTypes);
        HashSet<String> affectedItemSet = Sets.newHashSet("triggerItemDescriptor5", "triggerItemDescriptor6");
        affectedItemTypes.put("searchRepo", affectedItemSet);
        
        doAnswer(new Answer<Object>() {
        	public Object answer(InvocationOnMock invocation) throws Throwable {
        		endGate.countDown();
        		return null;
        	}
        }).when(ruleFeed).startFeed();
        
        indexingDeploymentListener.deploymentEvent(event);
        endGate.await();
        
        verify(ruleFeed).startFeed();
        verify(facetFeed, times(0)).startFeed();
        verify(categoryFeed, times(0)).startFeed();
        verify(searchServer, times(0)).onRepositoryItemChanged("searchRepo", affectedItemSet);
     }

    @Test
    public void testCategoryDeploymentEvent() throws RepositoryException, SearchServerException, IOException {
        DeploymentEvent event = mock(DeploymentEvent.class);
        when(event.getNewState()).thenReturn(1);

        Map<String, Set<String>> affectedItemTypes = Maps.newHashMap();
        when(event.getAffectedItemTypes()).thenReturn(affectedItemTypes);
        HashSet<String> affectedItemSet = Sets.newHashSet("triggerItemDescriptor7", "triggerItemDescriptor8");
        affectedItemTypes.put("productRepo", affectedItemSet);

        indexingDeploymentListener.deploymentEvent(event );

        verify(categoryFeed).startFeed();
        verify(ruleFeed, times(0)).startFeed();
        verifyZeroInteractions(ruleFeed);
        verify(facetFeed, times(0)).startFeed();
        verify(searchServer, times(0)).onRepositoryItemChanged("productRepo", affectedItemSet);
    }

    @Test
    public void testAllDeploymentEvent() throws RepositoryException, SearchServerException, IOException, InterruptedException {
    	final CountDownLatch endGate = new CountDownLatch(1);
    	DeploymentEvent event = mock(DeploymentEvent.class);
        when(event.getNewState()).thenReturn(1);

        Map<String, Set<String>> affectedItemTypes = Maps.newHashMap();
        when(event.getAffectedItemTypes()).thenReturn(affectedItemTypes);
        HashSet<String> affectedItemSet = Sets.newHashSet("triggerItemDescriptor1", "triggerItemDescriptor2", "triggerItemDescriptor3", "triggerItemDescriptor4", "triggerItemDescriptor5", "triggerItemDescriptor6");
        affectedItemTypes.put("searchRepo", affectedItemSet);
        affectedItemTypes.put("productRepo", Sets.newHashSet("triggerItemDescriptor7", "triggerItemDescriptor8"));
        
        doAnswer(new Answer<Object>() {
        	public Object answer(InvocationOnMock invocation) throws Throwable {
        		endGate.countDown();
        		return null;
        	}
        }).when(ruleFeed).startFeed();
        
        indexingDeploymentListener.deploymentEvent(event);
        endGate.await();

        //Should call all just once
        verify(categoryFeed).startFeed();
        verify(ruleFeed).startFeed();
        verify(facetFeed).startFeed();
        verify(searchServer).onRepositoryItemChanged("searchRepo", affectedItemSet);
    }
}

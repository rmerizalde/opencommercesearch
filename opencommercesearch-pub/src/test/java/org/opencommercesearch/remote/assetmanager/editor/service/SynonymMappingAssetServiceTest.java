package org.opencommercesearch.remote.assetmanager.editor.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import atg.adapter.gsa.ChangeAwareSet;
import atg.adapter.gsa.GSAItem;
import atg.remote.assetmanager.editor.service.AssetService;

import org.opencommercesearch.repository.SearchRepositoryItemDescriptor;
import org.opencommercesearch.repository.SynonymProperty;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;

import atg.remote.assetmanager.editor.model.AssetViewUpdate;
import atg.remote.assetmanager.editor.model.PropertyUpdate;
import atg.remote.assetmanager.editor.service.AssetEditorInfo;
import atg.repository.Repository;
import atg.repository.RepositoryItem;
import atg.service.asset.AssetWrapper;
import atg.userprofiling.Profile;

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


/**
 *
 * @author rmerizalde
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({BaseAssetService.class})
public class SynonymMappingAssetServiceTest {

    private SynonymMappingAssetService service = new SynonymMappingAssetService();

    @Mock
    private AssetEditorInfo assetEditorInfo;
    @Mock
    private AssetService assetService;
    @Mock
    private PropertyUpdate propertyUpdate;
    @Mock
    private Profile profile;
    @Mock
    private GSAItem role1, role2;
    @Mock
    private Repository searchRepository;
    @Mock
    private RepositoryItem synonym;
    @Mock
    private RepositoryItem currItem;
    @Mock 
    private AssetWrapper assertWrapper;
    
    private Set<AssetViewUpdate> updates;    
    
    @Before
    public void setup() throws Exception {
        updates = new HashSet<AssetViewUpdate>();
        service.setLoggingDebug(false);
        service.setSearchRepository(searchRepository);
        when(assetEditorInfo.getAssetService()).thenReturn(assetService);
        Set<String> allowedRoles = new HashSet<String>();
        allowedRoles.add("engineers");
        service.setAllowedRoles(allowedRoles);
        PowerMockito.mockStatic(BaseAssetService.class);
    }

    @Test
    public void test_invalidMapping() {
        when(propertyUpdate.getPropertyName()).thenReturn(SynonymProperty.MAPPING);
        when(propertyUpdate.getPropertyValue()).thenReturn("car");

        service.doValidatePropertyUpdate(assetEditorInfo, propertyUpdate);
        verify(assetService).addError(SynonymProperty.MAPPING, SynonymMappingAssetService.ERROR_INVALID_SYNONYM_MAPPING);
    }

    @Test
    public void test_validMapping() {
        when(propertyUpdate.getPropertyName()).thenReturn(SynonymProperty.MAPPING);
        when(propertyUpdate.getPropertyValue()).thenReturn("car,auto");

        service.doValidatePropertyUpdate(assetEditorInfo, propertyUpdate);
        verify(assetService, times(0)).addError(anyString(), anyString());
    }

    @Test
    public void test_invalidExplicitMapping() {
        when(propertyUpdate.getPropertyName()).thenReturn(SynonymProperty.MAPPING);
        when(propertyUpdate.getPropertyValue()).thenReturn("car=>");

        service.doValidatePropertyUpdate(assetEditorInfo, propertyUpdate);
        verify(assetService).addError(SynonymProperty.MAPPING, SynonymMappingAssetService.ERROR_INVALID_EXPLICIT_SYNONYM_MAPPING);
    }

    @Test
    public void test_invalidExplicitMappingOther() {
        when(propertyUpdate.getPropertyName()).thenReturn(SynonymProperty.MAPPING);
        when(propertyUpdate.getPropertyValue()).thenReturn("=>car");

        service.doValidatePropertyUpdate(assetEditorInfo, propertyUpdate);
        verify(assetService).addError(SynonymProperty.MAPPING, SynonymMappingAssetService.ERROR_INVALID_EXPLICIT_SYNONYM_MAPPING);
    }

    @Test
    public void test_validExplicitMapping() {
        when(propertyUpdate.getPropertyName()).thenReturn(SynonymProperty.MAPPING);
        when(propertyUpdate.getPropertyValue()).thenReturn("car=>auto");

        service.doValidatePropertyUpdate(assetEditorInfo, propertyUpdate);
        verify(assetService, times(0)).addError(anyString(), anyString());
    }

    @Test
    public void test_validExplicitMappingMoreThenOneArrow() {
        when(propertyUpdate.getPropertyName()).thenReturn(SynonymProperty.MAPPING);
        when(propertyUpdate.getPropertyValue()).thenReturn("car=>auto=>automobile");

        service.doValidatePropertyUpdate(assetEditorInfo, propertyUpdate);
        verify(assetService).addError(SynonymProperty.MAPPING, SynonymMappingAssetService.ERROR_INVALID_EXPLICIT_SYNONYM_MAPPING);        
    }
    
    @Test
    public void test_validUserPriviledges() {
        when(role1.getPropertyValue("name")).thenReturn("engineers");
        when(role2.getPropertyValue("name")).thenReturn("admins");
        ChangeAwareSet awareSet = new ChangeAwareSet(new HashSet<String>(), null, null);
        awareSet.add(role1);
        awareSet.add(role2);
        service.setUserProfile(profile);
        when(profile.getPropertyValue("roles")).thenReturn(awareSet);
        assertEquals(service.isNotPriviledged(),false);
    }
    
    @Test
    public void test_QueryParserSynonymListPriviledgedUser() {
        service.doValidateAccessPrivileges(assetEditorInfo, updates);
        verify(assetEditorInfo, Mockito.times(0));
    }
    
    @Test 
    public void test_QueryParserNotPriviledged() throws Exception {
        when(role2.getPropertyValue("name")).thenReturn("admins");
        ChangeAwareSet awareSet = new ChangeAwareSet(new HashSet<String>(), null, null);
        awareSet.add(role2);
        service.setUserProfile(profile);
        when(profile.getPropertyValue("roles")).thenReturn(awareSet);
        PowerMockito.doReturn(propertyUpdate).when(BaseAssetService.class, "findPropertyUpdate",Mockito.eq(SynonymProperty.SYNONYM_LIST), Mockito.eq(updates));
        when(propertyUpdate.getPropertyValue()).thenReturn("assestService:/search/synonymList/QueryParser");
        when(synonym.getPropertyValue("fileName")).thenReturn("query_synonyms");
        when(searchRepository.getItem("QueryParser", SearchRepositoryItemDescriptor.SYNONYM_LIST)).thenReturn(synonym);
        service.doValidateAccessPrivileges(assetEditorInfo, updates);
         verify(assetEditorInfo, Mockito.times(1));
    }
    
    @Test 
    public void test_SynonymMappingNotPriviledged() throws Exception {
        when(role2.getPropertyValue("name")).thenReturn("admins");
        ChangeAwareSet awareSet = new ChangeAwareSet(new HashSet<String>(), null, null);
        awareSet.add(role2);
        service.setUserProfile(profile);
        when(profile.getPropertyValue("roles")).thenReturn(awareSet);
        PowerMockito.doReturn(propertyUpdate).when(BaseAssetService.class, "findPropertyUpdate",Mockito.eq(SynonymProperty.MAPPING), Mockito.eq(updates));
        when(propertyUpdate.getPropertyValue()).thenReturn("assestService:/search/synonymList/QueryParser");
        when(assetEditorInfo.getAssetWrapper()).thenReturn(assertWrapper);
        when(assertWrapper.getAsset()).thenReturn(currItem);
        when(currItem.getPropertyValue(SearchRepositoryItemDescriptor.SYNONYM_LIST)).thenReturn(synonym);
        when(synonym.getPropertyValue("fileName")).thenReturn("query_synonyms");
        service.doValidateAccessPrivileges(assetEditorInfo, updates);
        verify(assetEditorInfo, Mockito.times(1));
    }
    
}


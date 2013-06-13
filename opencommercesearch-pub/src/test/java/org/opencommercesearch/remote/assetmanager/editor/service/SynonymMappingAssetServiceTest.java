package org.opencommercesearch.remote.assetmanager.editor.service;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import atg.remote.assetmanager.editor.service.AssetService;
import org.opencommercesearch.repository.SynonymProperty;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import atg.remote.assetmanager.editor.model.PropertyUpdate;
import atg.remote.assetmanager.editor.service.AssetEditorInfo;

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
@RunWith(MockitoJUnitRunner.class)
public class SynonymMappingAssetServiceTest {

    private SynonymMappingAssetService service = new SynonymMappingAssetService();

    @Mock
    private AssetEditorInfo assetEditorInfo;
    @Mock
    private AssetService assetService;
    @Mock
    private PropertyUpdate propertyUpdate;

    @Before
    public void setup() {
        service.setLoggingDebug(false);
        when(assetEditorInfo.getAssetService()).thenReturn(assetService);

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
}


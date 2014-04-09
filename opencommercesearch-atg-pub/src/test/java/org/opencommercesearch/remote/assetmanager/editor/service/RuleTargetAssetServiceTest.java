package org.opencommercesearch.remote.assetmanager.editor.service;

import static org.junit.Assert.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import atg.beans.DynamicPropertyDescriptor;
import atg.nucleus.ServiceMap;
import atg.remote.assetmanager.editor.service.AssetEditorInfo;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryItemDescriptor;
import atg.service.asset.AssetWrapper;

public class RuleTargetAssetServiceTest {

    @InjectMocks
    private RuleTargetAssetService ruleTargetAssetService = new RuleTargetAssetService();
    
    ServiceMap validatorMap;
    
    @Mock
    RedirectRuleAssetValidator redirectValidator;
    @Mock
    DefaultRuleAssetValidator defaultValidator;
    
    @Before
    public void setUp() throws Exception {        
        initMocks(this);
        validatorMap = mock(ServiceMap.class);
        ruleTargetAssetService.setValidatorMap(validatorMap);
        when(validatorMap.containsKey("redirectRule")).thenReturn(true);
        when(validatorMap.get("redirectRule")).thenReturn(redirectValidator);
        when(validatorMap.get("defaultRule")).thenReturn(defaultValidator);
    }

    @Test
    public void testValidateNewAsset() throws RepositoryException {
        Collection updates = new ArrayList();
        AssetEditorInfo editorInfo = mockEditorInfo("facetRule");
        ruleTargetAssetService.validateNewAsset(editorInfo, updates);
        verify(defaultValidator).validateNewAsset(editorInfo, updates);
        
        editorInfo = mockEditorInfo("redirectRule");
        ruleTargetAssetService.validateNewAsset(editorInfo, updates);
        verify(redirectValidator).validateNewAsset(editorInfo, updates);
    }

    @Test
    public void testValidateUpdateAsset() throws RepositoryException {
        Collection updates = new ArrayList();
        AssetEditorInfo editorInfo = mockEditorInfo("facetRule");
        ruleTargetAssetService.validateUpdateAsset(editorInfo, updates);
        verify(defaultValidator).validateUpdateAsset(editorInfo, updates);
        
        editorInfo = mockEditorInfo("redirectRule");
        ruleTargetAssetService.validateUpdateAsset(editorInfo, updates);
        verify(redirectValidator).validateUpdateAsset(editorInfo, updates);
    }

    private AssetEditorInfo mockEditorInfo(String itemDescriptorName) throws RepositoryException{
        AssetEditorInfo editorInfo = mock(AssetEditorInfo.class);
        AssetWrapper wrapper = mock(AssetWrapper.class);
        RepositoryItem repoItem = mock(RepositoryItem.class);
        RepositoryItemDescriptor itemDescriptor = mock(RepositoryItemDescriptor.class);
        when(itemDescriptor.getItemDescriptorName()).thenReturn(itemDescriptorName);
        DynamicPropertyDescriptor[] propertyDescriptor = {};
        when(itemDescriptor.getPropertyDescriptors()).thenReturn(propertyDescriptor );
        when(repoItem.getItemDescriptor()).thenReturn(itemDescriptor);
        when(wrapper.getAsset()).thenReturn(repoItem );
        when(editorInfo.getAssetWrapper()).thenReturn(wrapper );
        return editorInfo;
    }
}

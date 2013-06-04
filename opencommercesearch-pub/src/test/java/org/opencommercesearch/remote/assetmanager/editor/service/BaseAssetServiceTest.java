package org.opencommercesearch.remote.assetmanager.editor.service;

import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opencommercesearch.repository.BaseAssetProperty;

import atg.beans.DynamicPropertyDescriptor;
import atg.remote.assetmanager.editor.service.AssetEditorInfo;
import atg.repository.MutableRepositoryItem;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryItemDescriptor;
import atg.service.asset.AssetWrapper;
import atg.userprofiling.Profile;

/**
 * Test base asset service. Just make sure all review fields are generated with the proper information.
 * @author jmendez
 *
 */
public class BaseAssetServiceTest {

    @InjectMocks
    private BaseAssetService targetAssetService = new BaseAssetService();

    Profile userProfile;
    
    @Mock
    RedirectRuleAssetValidator redirectValidator;
    @Mock
    DefaultRuleAssetValidator defaultValidator;
    @Mock
    MutableRepositoryItem currentItem;
    
    @Before
    public void setUp() throws Exception {        
        initMocks(this);
        userProfile = mock(Profile.class);
        targetAssetService.setUserProfile(userProfile);
    }

    @Test
    public void testAddNewAsset() throws RepositoryException {
        AssetEditorInfo editorInfo = mockEditorInfo("synonymList");

        //Record expectations
        when(userProfile.getRepositoryId()).thenReturn("addasset-user");
        when(editorInfo.getAssetWrapper().getAsset()).thenReturn(currentItem);

        Collection updates = new ArrayList();
        targetAssetService.preAddAsset(editorInfo, updates);
        verify(currentItem).setPropertyValue(BaseAssetProperty.CREATED_BY, "addasset-user");
        verify(currentItem).setPropertyValue(eq(BaseAssetProperty.CREATION_DATE), any(Timestamp.class));
        verify(currentItem).setPropertyValue(BaseAssetProperty.LAST_MODIFIED_BY, "addasset-user");
        verify(currentItem).setPropertyValue(eq(BaseAssetProperty.LAST_MODIFIED_DATE), any(Timestamp.class));
    }

    @Test
    public void testUpdateAsset() throws RepositoryException {
        AssetEditorInfo editorInfo = mockEditorInfo("synonymList");
        
        //Record expectations
        when(userProfile.getRepositoryId()).thenReturn("updateasset-user");
        when(editorInfo.getAssetWrapper().getAsset()).thenReturn(currentItem);

        Collection updates = new ArrayList();
        targetAssetService.preAddAsset(editorInfo, updates);
        verify(currentItem).setPropertyValue(BaseAssetProperty.LAST_MODIFIED_BY, "updateasset-user");
        verify(currentItem).setPropertyValue(eq(BaseAssetProperty.LAST_MODIFIED_DATE), any(Timestamp.class));
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

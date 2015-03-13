package org.opencommercesearch.remote.assetmanager.editor.service;

import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opencommercesearch.repository.RuleProperty;

import atg.remote.assetmanager.editor.model.AssetViewUpdate;
import atg.remote.assetmanager.editor.model.PropertyEditorAssetViewUpdate;
import atg.remote.assetmanager.editor.model.PropertyUpdate;
import atg.remote.assetmanager.editor.service.AssetEditorInfo;
import atg.remote.assetmanager.editor.service.AssetService;
import atg.repository.MutableRepository;
import atg.repository.MutableRepositoryItem;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryItemDescriptor;
import atg.service.asset.AssetWrapper;

public class DefaultRuleAssetValidatorTest {

	private DefaultRuleAssetValidator  defaultRuleAssetValidator = new DefaultRuleAssetValidator();
	
	@Mock
	AssetEditorInfo editorInfo;
	@Mock
	AssetService assetService;
	@Mock
	RepositoryItem repoItem;
	
	@Mock
	MutableRepository mutableRepository;
	@Mock
	MutableRepositoryItem mutableRepositoryItem;
	
	Collection updates;
	
	@Before
	public void setUp() throws Exception {
		initMocks(this);
		updates = new ArrayList();
		when(editorInfo.getAssetService()).thenReturn(assetService);
		
		AssetWrapper assetWrapper = mock(AssetWrapper.class);
		when(editorInfo.getAssetWrapper()).thenReturn(assetWrapper );
		when(assetWrapper.getAsset()).thenReturn(repoItem);
		
		when(repoItem.getRepository()).thenReturn(mutableRepository);
		RepositoryItemDescriptor itemDescriptor = mock(RepositoryItemDescriptor.class);
		when(itemDescriptor.getItemDescriptorName()).thenReturn("itemDescriptor");
		when(repoItem.getItemDescriptor()).thenReturn(itemDescriptor );
		when(mutableRepository.getItemForUpdate(anyString(), anyString())).thenReturn(mutableRepositoryItem);

		when(repoItem.getPropertyValue(RuleProperty.START_DATE)).thenReturn(new Timestamp(2000));
		when(repoItem.getPropertyValue(RuleProperty.END_DATE)).thenReturn(new Timestamp(2500));

		defaultRuleAssetValidator.setLoggingInfo(false);
		defaultRuleAssetValidator.setLoggingDebug(false);
	}

	@Test
	public void testValidateNewAsset() {
		updates.add(mockAssetView("dateChange", "03/11/2013"));
		defaultRuleAssetValidator.validateNewAsset(editorInfo, updates, null);
		verify(assetService, never()).addError(anyString(), anyString());
	}

	@Test
	public void testValidateNewAssetTargetWithValidQuery() {		
		//scenario where we add a new asset  only setting it's target. Query is not specified
		//ERROR IS EXPECTED
		updates.add(mockAssetView("target", DefaultRuleAssetValidator.SEARCH_PAGES));
		defaultRuleAssetValidator.validateNewAsset(editorInfo, updates, null);
		verify(assetService).addError(anyString(), anyString());		
	}
	
	@Test
	public void testValidateUpdateAssetNoTargetOrQueryChange() {
		updates.add(mockAssetView("dateChange", "03/11/2013"));
		defaultRuleAssetValidator.validateUpdateAsset(editorInfo, updates, null);
		verify(assetService, never()).addError(anyString(), anyString());
	}
	
	@Test
	public void testValidateUpdateQueryWithNullValue1() {	
		//scenario where we update the query for a search page to a null value.
		//ERROR IS EXPECTED
		when(repoItem.getPropertyValue(RuleProperty.TARGET)).thenReturn(DefaultRuleAssetValidator.SEARCH_PAGES);
		updates.add(mockAssetView("query", null));		
		defaultRuleAssetValidator.validateUpdateAsset(editorInfo, updates, null);
		verify(assetService).addError(eq(RuleProperty.QUERY), anyString());	
	}
	
	@Test
	public void testValidateUpdateQueryWithNullValue2() {	
		//scenario where we update the query for an all pages to a null value.
		//ERROR SHOULDN'T NOT BE ADDED
		when(repoItem.getPropertyValue(RuleProperty.TARGET)).thenReturn(DefaultRuleAssetValidator.ALL_PAGES);
		updates.add(mockAssetView("query", null));		
		defaultRuleAssetValidator.validateUpdateAsset(editorInfo, updates, null);
		verify(assetService, never()).addError(anyString(), anyString());
	}
	
	@Test
	public void testValidateUpdateQueryWithValidValue1() {	
		//scenario where we update the query for a search page to a valid value.
		//ERROR SHOULDN'T NOT BE ADDED
		when(repoItem.getPropertyValue(RuleProperty.TARGET)).thenReturn(DefaultRuleAssetValidator.SEARCH_PAGES);
		updates.add(mockAssetView("query", "validValue"));		
		defaultRuleAssetValidator.validateUpdateAsset(editorInfo, updates, null);
		verify(assetService, never()).addError(anyString(), anyString());
	}
	
	@Test
	public void testValidateUpdateQueryWithValidValue2() {	
		//scenario where we update the query for a category page to a valid value.
		//ERROR SHOULDN'T NOT BE ADDED
		when(repoItem.getPropertyValue(RuleProperty.TARGET)).thenReturn(DefaultRuleAssetValidator.CATEGORY_PAGES);
		updates.add(mockAssetView("query", "validValue"));		
		defaultRuleAssetValidator.validateUpdateAsset(editorInfo, updates, null);
		verify(assetService, never()).addError(anyString(), anyString());
	}
	
	@Test
	public void testValidateUpdateSetDefaultQuery() throws RepositoryException {	
		//scenario where we update the target to allpages and query is not set. it should be set as a default value of "*"
		//ERROR SHOULDN'T NOT BE ADDED AND  DEFAULT QUERY SHOULD BE PERSISTED
		updates.add(mockAssetView("target", DefaultRuleAssetValidator.ALL_PAGES));	
		defaultRuleAssetValidator.validateUpdateAsset(editorInfo, updates, null);
		verify(assetService, never()).addError(anyString(), anyString());
		verify(mutableRepository).updateItem(mutableRepositoryItem);
		verify(mutableRepositoryItem).setPropertyValue(RuleProperty.QUERY, "*");
	}
	
	@Test
	public void testValidateUpdateTargetWithNullQuery1() {		
		//scenario where we update both target and query. We are setting the query to null
		//ERROR IS EXPECTED
		updates.add(mockAssetView("target", DefaultRuleAssetValidator.SEARCH_PAGES));
		updates.add(mockAssetView("query", null));		
		defaultRuleAssetValidator.validateUpdateAsset(editorInfo, updates, null);
		verify(assetService).addError(eq(RuleProperty.QUERY), anyString());		
	}
	
	@Test
	public void testValidateUpdateTargetWithNullQuery2() {		
		//scenario where we update only target. Get query from persisted repo item value, but it's null
		//ERROR IS EXPECTED
		updates.add(mockAssetView("target", DefaultRuleAssetValidator.SEARCH_PAGES));
		when(repoItem.getPropertyValue(RuleProperty.QUERY)).thenReturn(null);
		defaultRuleAssetValidator.validateUpdateAsset(editorInfo, updates, null);
		verify(assetService).addError(eq(RuleProperty.QUERY), anyString());		
	}
	
	@Test
	public void testValidateUpdateTargetWithValidQuery1() {		
		//scenario where we update both target and query
		//ERROR SHOULDN'T NOT BE ADDED
		updates.add(mockAssetView("target", DefaultRuleAssetValidator.SEARCH_PAGES));
		updates.add(mockAssetView("query", "validQuery"));		
		defaultRuleAssetValidator.validateUpdateAsset(editorInfo, updates, null);
		verify(assetService, never()).addError(anyString(), anyString());		
	}

	@Test
	public void testValidateUpdateTargetWithValidQuery2() {		
		//scenario where we update only target. Get query from persisted repo item value
		//ERROR SHOULDN'T NOT BE ADDED
		updates.add(mockAssetView("target", DefaultRuleAssetValidator.SEARCH_PAGES));
		when(repoItem.getPropertyValue(RuleProperty.QUERY)).thenReturn("validQuery");
		defaultRuleAssetValidator.validateUpdateAsset(editorInfo, updates, null);
		verify(assetService, never()).addError(anyString(), anyString());		
	}
	
    @Test
    public void testValidateUpdateLifeTimeWithBadDates() {      
        //scenario where we update the start and end date, but the end date is before start date.
        //ERROR SHOULDN'T BE ADDED
        updates.add(mockAssetView("endDate", new Timestamp(25000).toString()));
        updates.add(mockAssetView("startDate", new Timestamp(20000).toString()));
        defaultRuleAssetValidator.validateUpdateAsset(editorInfo, updates, null);
        verify(assetService, never()).addError(eq(RuleProperty.END_DATE), anyString());     
    }
    
    @Test
    public void testValidateUpdateLifeTimeWithGoodDates() {      
        //scenario where we update the start and end date, but the end date is before start date.
        //ERROR IS EXPECTED
        updates.add(mockAssetView("endDate", new Timestamp(20000).toString()));
        updates.add(mockAssetView("startDate", new Timestamp(25000).toString()));
        defaultRuleAssetValidator.validateUpdateAsset(editorInfo, updates, null);
        verify(assetService).addError(eq(RuleProperty.END_DATE), anyString());     
    }
	
	private AssetViewUpdate mockAssetView(String propName, String propValue){
		PropertyEditorAssetViewUpdate assetView = mock(PropertyEditorAssetViewUpdate.class);
		Collection<PropertyUpdate> propertyUpdateCollection = new ArrayList<PropertyUpdate>();
		
		PropertyUpdate propertyUpdate = mock(PropertyUpdate.class);
		when(propertyUpdate.getPropertyName()).thenReturn(propName);
		when(propertyUpdate.getPropertyValue()).thenReturn(propValue);
		propertyUpdateCollection.add(propertyUpdate);
		when(assetView.getPropertyUpdates()).thenReturn(propertyUpdateCollection);
		
		return assetView;
		
	}
}

package org.opencommercesearch.remote.assetmanager.editor.service;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.verification.VerificationMode;
import org.opencommercesearch.repository.RuleProperty;

import atg.remote.assetmanager.editor.model.AssetViewUpdate;
import atg.remote.assetmanager.editor.model.PropertyEditorAssetViewUpdate;
import atg.remote.assetmanager.editor.model.PropertyUpdate;
import atg.remote.assetmanager.editor.service.AssetEditorInfo;
import atg.remote.assetmanager.editor.service.AssetService;
import atg.repository.RepositoryItem;
import atg.service.asset.AssetWrapper;

public class RedirectRuleAssetValidatorTest {

	RedirectRuleAssetValidator redirectRuleAssetValidator = new RedirectRuleAssetValidator();
	
	@Mock
	AssetEditorInfo editorInfo;
	@Mock
	AssetService assetService;
	@Mock
	RepositoryItem repoItem;
	
	Collection updates;
	
	@Before
	public void setUp() throws Exception {
		initMocks(this);
		
		initMocks(this);
		updates = new ArrayList();
		when(editorInfo.getAssetService()).thenReturn(assetService);
		
		AssetWrapper assetWrapper = mock(AssetWrapper.class);
		when(editorInfo.getAssetWrapper()).thenReturn(assetWrapper );
		when(assetWrapper.getAsset()).thenReturn(repoItem);

		when(repoItem.getPropertyValue(RuleProperty.START_DATE)).thenReturn(new Timestamp(2000));
        when(repoItem.getPropertyValue(RuleProperty.END_DATE)).thenReturn(new Timestamp(2500));

		redirectRuleAssetValidator.setLoggingInfo(false);
		redirectRuleAssetValidator.setLoggingDebug(false);
	}

	@Test
	public void testValidateNewAsset() {
		updates.add(mockAssetView("target", DefaultRuleAssetValidator.SEARCH_PAGES));
		updates.add(mockAssetView("query", "validQuery"));
		redirectRuleAssetValidator.validateNewAsset(editorInfo, updates);
		verify(assetService, never()).addError(anyString(), anyString());
	}

	@Test
	public void testValidateNewAssetQueryAll() {
		updates.add(mockAssetView("target", DefaultRuleAssetValidator.SEARCH_PAGES));
		updates.add(mockAssetView("query", "*"));
		redirectRuleAssetValidator.validateNewAsset(editorInfo, updates);
		verify(assetService, atLeastOnce()).addError(eq(RuleProperty.QUERY), anyString());
	}
	
	@Test
	public void testValidateNewAssetAllPages() {
		updates.add(mockAssetView("target", DefaultRuleAssetValidator.ALL_PAGES));
		updates.add(mockAssetView("query", "validQuery"));
		redirectRuleAssetValidator.validateNewAsset(editorInfo, updates);
		verify(assetService).addError(eq(RuleProperty.TARGET), anyString());
	}
	
	@Test
	public void testValidateNewAssetCategoryPages() {
		updates.add(mockAssetView("target", DefaultRuleAssetValidator.CATEGORY_PAGES));
		updates.add(mockAssetView("query", "validQuery"));
		redirectRuleAssetValidator.validateNewAsset(editorInfo, updates);
		verify(assetService).addError(eq(RuleProperty.TARGET), anyString());
	}
	
	@Test
	public void testValidateUpdateAssetExistingTarget1() {
		//scenario: if the existing target is search page and we update the query to "*" it should fail 
		when(repoItem.getPropertyValue(RuleProperty.TARGET)).thenReturn(DefaultRuleAssetValidator.SEARCH_PAGES);
		updates.add(mockAssetView("query", "*"));
		redirectRuleAssetValidator.validateUpdateAsset(editorInfo, updates);
		verify(assetService, atLeastOnce()).addError(eq(RuleProperty.QUERY), anyString());
	}
	
	@Test
	public void testValidateUpdateAssetExistingTarget2() {
		//scenario: if the existing target is all pages and we update the query to "*" it should fail 
		when(repoItem.getPropertyValue(RuleProperty.TARGET)).thenReturn(DefaultRuleAssetValidator.CATEGORY_PAGES);
		updates.add(mockAssetView("query", "*"));
		redirectRuleAssetValidator.validateUpdateAsset(editorInfo, updates);
		verify(assetService).addError(eq(RuleProperty.QUERY), anyString());
	}
	
	@Test
	public void testValidateUpdateAssetOtherAttr1() {
		//scenario: if we change another attribute not related to target and query was valid it should pass
		when(repoItem.getPropertyValue(RuleProperty.TARGET)).thenReturn(DefaultRuleAssetValidator.SEARCH_PAGES);
		when(repoItem.getPropertyValue(RuleProperty.QUERY)).thenReturn("validQuery");
		updates.add(mockAssetView("date", "03/11/2013"));
		redirectRuleAssetValidator.validateUpdateAsset(editorInfo, updates);
		verify(assetService, never()).addError(anyString(), anyString());
	}
	
	@Test
	public void testValidateUpdateAssetOtherAttr2() {
		//scenario: if we change another attribute not related to target and query was invalid it should fail
		when(repoItem.getPropertyValue(RuleProperty.TARGET)).thenReturn(DefaultRuleAssetValidator.SEARCH_PAGES);
		when(repoItem.getPropertyValue(RuleProperty.QUERY)).thenReturn("*");
		updates.add(mockAssetView("date", "03/11/2013"));
		redirectRuleAssetValidator.validateUpdateAsset(editorInfo, updates);
		verify(assetService).addError(eq(RuleProperty.QUERY), anyString());
	}
	
	@Test
	public void testValidateUpdateAssetEmptyQuery() {
		//scenario: if we change another attribute not related to target and query was empty it should fail
		when(repoItem.getPropertyValue(RuleProperty.TARGET)).thenReturn(DefaultRuleAssetValidator.SEARCH_PAGES);
		updates.add(mockAssetView("date", "03/11/2013"));
		redirectRuleAssetValidator.validateUpdateAsset(editorInfo, updates);
		verify(assetService).addError(eq(RuleProperty.QUERY), anyString());
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

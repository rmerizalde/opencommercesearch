package org.opencommercesearch.remote.assetmanager.editor.service;

import java.util.Collection;

import org.apache.commons.lang.StringUtils;
import org.opencommercesearch.repository.RuleProperty;

import atg.nucleus.GenericService;
import atg.remote.assetmanager.editor.model.PropertyUpdate;
import atg.remote.assetmanager.editor.service.AssetEditorInfo;
import atg.repository.RepositoryItem;

public class RedirectRuleAssetValidator extends DefaultRuleAssetValidator {

	private static final String QUERY_ERROR_MSG = "Set the query attribute to something different than * for redirect rules.";
	private static final String TARGET_ERROR_MSG = "Redirect rules only support 'search pages' target";

	@Override
	public void validateNewAsset(AssetEditorInfo editorInfo, Collection updates) {
		validateQuery(editorInfo, updates);
	}
	
	@Override
	public void validateUpdateAsset(AssetEditorInfo editorInfo, Collection updates) {
		validateQuery(editorInfo, updates);
	}

	private void validateQuery(AssetEditorInfo editorInfo, Collection updates){
		
		PropertyUpdate targetProperty  = findPropertyUpdate(RuleProperty.TARGET, updates);
		RepositoryItem currentItem = (RepositoryItem) editorInfo.getAssetWrapper().getAsset();
		
		if(targetProperty != null ) {
			//if we are updating the target to something other that SEARCH_PAGES, then show an error
			if(! SEARCH_PAGES.equals(targetProperty.getPropertyValue())) {
				editorInfo.getAssetService().addError(RuleProperty.TARGET, TARGET_ERROR_MSG);
			}
		} else {
			String target = (String) currentItem.getPropertyValue(RuleProperty.TARGET);
			if(! SEARCH_PAGES.equals(target)) {
				editorInfo.getAssetService().addError(RuleProperty.TARGET, TARGET_ERROR_MSG);
			}
		}
		
		boolean isEmpty = true;
		PropertyUpdate queryProperty  = findPropertyUpdate(RuleProperty.QUERY, updates);
		if(queryProperty != null ) {
			//if we are updating the query to *, then show an error
			String query = (String) queryProperty.getPropertyValue();
			if("*".equals(query)){
				editorInfo.getAssetService().addError(RuleProperty.QUERY, QUERY_ERROR_MSG);
			}
			
			if(StringUtils.isNotBlank(query)) {
				isEmpty = false;
			}
		} else {
			//if we change another attribute but the query was set to * somehow, then show error
			String query = (String) currentItem.getPropertyValue(RuleProperty.QUERY);
			if("*".equals(query)) {
				editorInfo.getAssetService().addError(RuleProperty.QUERY, QUERY_ERROR_MSG);
			}
			
			if(StringUtils.isNotBlank(query)) {
				isEmpty = false;
			}
		}
		
		if(isEmpty) {
			editorInfo.getAssetService().addError(RuleProperty.QUERY, ERROR_MSG);
		}
	}
}

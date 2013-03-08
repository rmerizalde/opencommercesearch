package org.opencommercesearch.remote.assetmanager.editor.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.opencommercesearch.repository.RuleProperty;

import atg.nucleus.ResolvingMap;
import atg.nucleus.ServiceMap;
import atg.remote.assetmanager.editor.model.AssetViewUpdate;
import atg.remote.assetmanager.editor.model.PropertyEditorAssetViewUpdate;
import atg.remote.assetmanager.editor.model.PropertyUpdate;
import atg.remote.assetmanager.editor.service.AssetEditorInfo;
import atg.remote.assetmanager.editor.service.RepositoryAssetServiceImpl;
import atg.repository.MutableRepository;
import atg.repository.MutableRepositoryItem;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import atg.service.asset.AssetWrapper;

public class RuleTargetAssetService extends RepositoryAssetServiceImpl {
		
	private static final String DEFAULT_RULE = "defaultRule";
	private ServiceMap validatorMap = new ServiceMap();
		
	@Override
	public void validateNewAsset(AssetEditorInfo editorInfo, Collection updates) {
		super.validateNewAsset(editorInfo, updates);
		doValidation(editorInfo, updates, true);
	}

	@Override
	public void validateUpdateAsset(AssetEditorInfo editorInfo, Collection updates) {
		super.validateUpdateAsset(editorInfo, updates);
		doValidation(editorInfo, updates, false);
	}
	
	public void doValidation(AssetEditorInfo editorInfo, Collection updates, boolean isNew) {
		RepositoryItem item = (RepositoryItem) editorInfo.getAssetWrapper().getAsset();
		if ( item != null ) {
			try {
				DefaultRuleAssetValidator validator = (DefaultRuleAssetValidator) validatorMap.get(DEFAULT_RULE);
				
				String itemType = item.getItemDescriptor().getItemDescriptorName();
				if (validatorMap.containsKey(itemType)) {
					validator = (DefaultRuleAssetValidator) validatorMap.get(itemType);
				}
				
				if (isNew) {
					validator.validateNewAsset(editorInfo, updates);
				} else {
					validator.validateUpdateAsset(editorInfo, updates);
				}
			} catch (RepositoryException e) {
				if (isLoggingError()) {
					logError(e);
				}
			}
		}
	}

	public ServiceMap getValidatorMap() {
		return validatorMap;
	}

	public void setValidatorMap(ServiceMap validatorMap) {
		this.validatorMap = validatorMap;
	}
	
}

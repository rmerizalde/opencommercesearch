package org.opencommercesearch.remote.assetmanager.editor.service;

import java.util.Collection;

import org.apache.commons.lang.StringUtils;
import org.opencommercesearch.repository.RuleProperty;

import atg.nucleus.GenericService;
import atg.remote.assetmanager.editor.model.AssetViewUpdate;
import atg.remote.assetmanager.editor.model.PropertyEditorAssetViewUpdate;
import atg.remote.assetmanager.editor.model.PropertyUpdate;
import atg.remote.assetmanager.editor.service.AssetEditorInfo;
import atg.repository.MutableRepository;
import atg.repository.MutableRepositoryItem;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;

public class DefaultRuleAssetValidator extends GenericService {
	
	protected static final String CATEGORY_PAGES = "Category Pages";
	protected static final String SEARCH_PAGES = "Search Pages";
	protected static final String ALL_PAGES = "All Pages";
	
	protected static final String ERROR_MSG = "Query can't be empty for search pages. Provide a query or * to match all queries";
	
	public void validateNewAsset(AssetEditorInfo editorInfo, Collection updates) {

		PropertyUpdate targetProperty  = findPropertyUpdate(RuleProperty.TARGET, updates);
		PropertyUpdate queryProperty  = findPropertyUpdate(RuleProperty.QUERY, updates);
		
		if ( targetProperty != null || queryProperty != null) {
			validateTargetAndQueryUpdate(editorInfo, targetProperty, queryProperty);
		}
	}

	public void validateUpdateAsset(AssetEditorInfo editorInfo, Collection updates) {

		PropertyUpdate targetProperty  = findPropertyUpdate(RuleProperty.TARGET, updates);
		PropertyUpdate queryProperty  = findPropertyUpdate(RuleProperty.QUERY, updates);
		
		if ( targetProperty != null || queryProperty != null) {
			validateTargetAndQueryUpdate(editorInfo, targetProperty, queryProperty);
		}
	}
	
	/**
	 * Validate that if we select a target = searchPages, then the query parameter should be populated
	 * If we selected allPages or categoryPages, then the query will be default to * if the user didn't provided another value
	 */
	protected void validateTargetAndQueryUpdate(AssetEditorInfo editorInfo, PropertyUpdate targetProperty, PropertyUpdate queryProperty) {
		
		String targetValue = null; 
		
		if(targetProperty != null) {
			targetValue = (String) targetProperty.getPropertyValue();
		}
		if(StringUtils.isBlank(targetValue)) {
			targetValue = getPersistedTarget(editorInfo); 
		}
		
		if(isLoggingInfo()){
			logInfo("processing target: " + targetValue);
		}
		
		if(ALL_PAGES.equals(targetValue)) {
			setDefaultQuery(editorInfo, queryProperty);
		} else if(SEARCH_PAGES.equals(targetValue)) {
			
			if(queryProperty != null) {
				//scenario where we are providing a new query term or we are changing it
				if (StringUtils.isBlank((String) queryProperty.getPropertyValue())) {
					editorInfo.getAssetService().addError(queryProperty.getPropertyName(), ERROR_MSG);
				}
			} else {
				//scenario where we are updating this asset but the query term wasn't changed
				RepositoryItem currentItem = (RepositoryItem) editorInfo.getAssetWrapper().getAsset();
				if (!hasQuery(currentItem)) {
					if(isLoggingInfo()){
						logInfo("adding error cause asset doesn't have query set for search pages scenario");
					}
					editorInfo.getAssetService().addError(RuleProperty.QUERY, ERROR_MSG);
				}
			}
			
		} else if(CATEGORY_PAGES.equals(targetValue)) {
			setDefaultQuery(editorInfo, queryProperty);
		}
	}
	
	protected boolean hasQuery(RepositoryItem currentItem) {
		String query = (String) currentItem.getPropertyValue(RuleProperty.QUERY);
		return StringUtils.isNotBlank(query);
	}

	
	protected String getPersistedTarget(AssetEditorInfo editorInfo) {
		RepositoryItem currentItem = (RepositoryItem) editorInfo.getAssetWrapper().getAsset();
		return (String) currentItem.getPropertyValue(RuleProperty.TARGET);
	}
	
	protected void setDefaultQuery(AssetEditorInfo editorInfo, PropertyUpdate queryProperty) {
		RepositoryItem currentItem = (RepositoryItem) editorInfo.getAssetWrapper().getAsset();
		//only set a default query if it didn't had already a query value or if this update
		//didn't change the query value
		if (!hasQuery(currentItem) && queryProperty == null) {
			try {
				MutableRepository rep = (MutableRepository) currentItem.getRepository();
				MutableRepositoryItem mutableItem = rep.getItemForUpdate(currentItem.getRepositoryId(), currentItem.getItemDescriptor().getItemDescriptorName());
				mutableItem.setPropertyValue(RuleProperty.QUERY, "*");
				rep.updateItem(mutableItem);
			} catch (RepositoryException e) {
				editorInfo.getAssetService().addError("error adding default query to rule:" +currentItem.getRepositoryId());
			}
		} else {
			if (isLoggingDebug()) {
				logDebug("category or all pages target had a specific query. Keeping it");
			}
		}
	}
	
	/**
	 * Find the element that matches the propName from the updates collection
	 * @param propName The property name we are looking
	 * @param updates The collection of updates
	 * @return The element within the collection that matches the property name specificed. Otherwise returns null
	 */
	protected PropertyUpdate findPropertyUpdate(String propName, Collection updates) {
        if (updates != null) {
            for (Object update : updates) {
                AssetViewUpdate viewUpdate = (AssetViewUpdate) update;
                if ((viewUpdate instanceof PropertyEditorAssetViewUpdate)) {
                    PropertyEditorAssetViewUpdate propertyEditorViewUpdate = (PropertyEditorAssetViewUpdate) viewUpdate;
                    Collection propertyUpdates = propertyEditorViewUpdate.getPropertyUpdates();
                    if (propertyUpdates != null) {
                        for (Object propUpdate : propertyUpdates) {
                            PropertyUpdate propertyUpdate = (PropertyUpdate) propUpdate;
                            if (propName.equals(propertyUpdate.getPropertyName())) {
                                // Get the data for this property... though we
                                // need to determine if the property is scalar
                                // or collection
                                return propertyUpdate;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

}

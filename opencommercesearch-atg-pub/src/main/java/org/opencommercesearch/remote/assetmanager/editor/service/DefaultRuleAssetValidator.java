package org.opencommercesearch.remote.assetmanager.editor.service;

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

import java.sql.Timestamp;
import java.util.Collection;

import org.apache.commons.lang.StringUtils;
import org.opencommercesearch.repository.RuleProperty;

import atg.nucleus.GenericService;
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
    protected static final String NULL_PROPERTY_ERROR_MSG = "This field can't be empty, please enter a valid value";
    protected static final String INVALID_END_DATE_ERROR_MSG = "End date must be after start date";

    /**
     * Called when an asset is created. Ensures that the entered fields are valid.
     * @param editorInfo The current editor information (such as the asset being updated)
     * @param updates Collection of updates for the current asset. For example, fields that now have a value.
     */
    public void validateNewAsset(AssetEditorInfo editorInfo, Collection updates) {
        doValidation(editorInfo, updates);
        doDateValidation(editorInfo, updates);
    }    

    /**
     * Called when an asset is updated. Ensures that the entered fields are valid.
     * @param editorInfo The current editor information (such as the asset being updated)
     * @param updates Collection of updates for the current asset. For example, fields that now have a value.
     */
    public void validateUpdateAsset(AssetEditorInfo editorInfo, Collection updates) {
        doValidation(editorInfo, updates);
        doDateValidation(editorInfo, updates);
    }
    
    /**
     * Performs the actual validation.
     * <p/>
     * Subclasses should override this method if more specific validation is required.
     * @param editorInfo The current editor information (such as the asset being updated)
     * @param updates Collection of updates for the current asset. For example, fields that now have a value.
     */
    public void doValidation(AssetEditorInfo editorInfo, Collection updates) {
        PropertyUpdate targetProperty  = BaseAssetService.findPropertyUpdate(RuleProperty.TARGET, updates);
        PropertyUpdate queryProperty  = BaseAssetService.findPropertyUpdate(RuleProperty.QUERY, updates);
        
        if ( targetProperty != null || queryProperty != null) {
            validateTargetAndQueryUpdate(editorInfo, targetProperty, queryProperty);
        }
    }
    
    /**
     * Performs common rule date validation.
     * <p/>
     * All rules must have an end and start date. This method ensures that both dates are valid.
     * @param editorInfo The current editor information (such as the asset being updated)
     * @param updates Collection of updates for the current asset. For example, fields that now have a value.
     */
    public void doDateValidation(AssetEditorInfo editorInfo, Collection updates) {
        //Validate start and end dates. You shouldn't be able to specify an end date before the given start date.
        PropertyUpdate startDateProperty = BaseAssetService.findPropertyUpdate(RuleProperty.START_DATE, updates);
        PropertyUpdate endDateProperty = BaseAssetService.findPropertyUpdate(RuleProperty.END_DATE, updates);
        
        validateStartAndEndDateUpdate(editorInfo, startDateProperty, endDateProperty);
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

    /**
     * Checks that the start and end date are properly specified.
     * <p/>
     * A common error would be to enter an end date which is before the given start date. 
     */
    protected void validateStartAndEndDateUpdate(AssetEditorInfo editorInfo, PropertyUpdate startDateProperty, PropertyUpdate endDateProperty) {
        Timestamp startDate = null;
        Timestamp endDate = null;

        if(startDateProperty == null) {
            //This field wasn't updated, so fetch the value from the asset.
            RepositoryItem currentItem = (RepositoryItem) editorInfo.getAssetWrapper().getAsset();
            startDate = (Timestamp) currentItem.getPropertyValue(RuleProperty.START_DATE);
        }
        else {
            String startDateValue = (String)startDateProperty.getPropertyValue();

            if(startDateValue != null && !startDateValue.isEmpty()) {
                startDate = Timestamp.valueOf(startDateValue); //Timestamp.valueOf understands ISO 8601 date format.
            }
        }

        if(endDateProperty == null) {
            //This field wasn't updated, so fetch the value from the asset.
            RepositoryItem currentItem = (RepositoryItem) editorInfo.getAssetWrapper().getAsset();
            endDate = (Timestamp) currentItem.getPropertyValue(RuleProperty.END_DATE);
        }
        else {
            String endDateValue = (String)endDateProperty.getPropertyValue();
            if(endDateValue == null || endDateValue.isEmpty()) {
                editorInfo.getAssetService().addError(RuleProperty.END_DATE, NULL_PROPERTY_ERROR_MSG);
                return; //Don't do more validation.
            }
            else {
                endDate = Timestamp.valueOf(endDateValue); //Timestamp.valueOf understands ISO 8601 date format.
            }
        }

        if(startDate != null && endDate.before(startDate)) {
            editorInfo.getAssetService().addError(RuleProperty.END_DATE, INVALID_END_DATE_ERROR_MSG);
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
}

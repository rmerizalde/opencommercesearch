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

import java.util.Collection;

import org.apache.commons.lang.StringUtils;
import org.opencommercesearch.repository.RuleProperty;

import atg.remote.assetmanager.editor.model.PropertyUpdate;
import atg.remote.assetmanager.editor.service.AssetEditorInfo;
import atg.repository.RepositoryItem;

public class RedirectRuleAssetValidator extends DefaultRuleAssetValidator {

    private static final String QUERY_ERROR_MSG = "Set the query attribute to something different than * for redirect rules.";
    private static final String TARGET_ERROR_MSG = "Redirect rules only support 'search pages' target";

    @Override
    public void doValidation(AssetEditorInfo editorInfo, Collection updates) {
        validateQuery(editorInfo, updates);
    }

    private void validateQuery(AssetEditorInfo editorInfo, Collection updates){
        
        PropertyUpdate targetProperty  = BaseAssetService.findPropertyUpdate(RuleProperty.TARGET, updates);
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
        PropertyUpdate queryProperty  = BaseAssetService.findPropertyUpdate(RuleProperty.QUERY, updates);
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

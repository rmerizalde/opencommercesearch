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

import atg.nucleus.ServiceMap;
import atg.remote.assetmanager.editor.service.AssetEditorInfo;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;

public class RuleTargetAssetService extends BaseAssetService {
        
    private static final String DEFAULT_RULE = "defaultRule";
    private ServiceMap validatorMap = new ServiceMap();

    @Override
    public void validateNewAsset(AssetEditorInfo editorInfo, Collection updates) {
        //super.validateNewAsset(editorInfo, updates);
        doValidation(editorInfo, updates, true);
    }

    @Override
    public void validateUpdateAsset(AssetEditorInfo editorInfo, Collection updates) {
        //super.validateUpdateAsset(editorInfo, updates);
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

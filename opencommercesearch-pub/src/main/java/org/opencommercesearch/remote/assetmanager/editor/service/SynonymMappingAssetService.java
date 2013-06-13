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
import org.opencommercesearch.repository.SynonymProperty;

import atg.remote.assetmanager.editor.model.PropertyUpdate;
import atg.remote.assetmanager.editor.service.AssetEditorInfo;

/**
 * This class is used to validate the synonym mappings created in the BCC. Solr
 * supports two mapping types: explicit and equivalent.
 * 
 * A explicit mapping uses the arrow (=>) to map one ore more token sequences in
 * the left hand of the => with all alternatives in right hand side of it. For
 * example:
 * 
 * ipod, i-pod, i pod => ipod, i-pod, i pod
 * 
 * Equivalent synonyms are simple lists of token sequences separated by a comma.
 * For example:
 * 
 * ipod, i-pod, i pod
 * 
 * How this interpreted depends on the expand parameter. If the expand parameter
 * is set to true, the the previous examples is the same as:
 * 
 * ipod, i-pod, i pod => ipod, i-pod, i pod
 * 
 * If expand is set to false, then the it is equivalent to:
 * 
 * ipod, i-pod, i pod => ipod
 * 
 * As a note, Solr will merge multiple synonym mappings. For example:
 * 
 * ipod => i-pod ipod => i pod
 * 
 * would be equivalent to:
 * 
 * ipod => i-pod, i pod
 * 
 * @author rmerizalde
 * 
 */
public class SynonymMappingAssetService extends BaseAssetService {
    //@TODO use locale for messages
    public  static final String ERROR_INVALID_SYNONYM_MAPPING = "Must a be a comma-separated list";
    public  static final String ERROR_INVALID_EXPLICIT_SYNONYM_MAPPING = "Must have a expression on each side of the arrow";

    private static final char SEPARATOR = ',';
    private static final String ARROW = "=>";

    /**
     * Do the mapping validation on new synonym mappings.
     */
    @Override
    public void validateNewAsset(AssetEditorInfo pEditorInfo, Collection pUpdates) {
        super.validateNewAsset(pEditorInfo, pUpdates);
        PropertyUpdate mappingPropUpdate = BaseAssetService.findPropertyUpdate(SynonymProperty.MAPPING, pUpdates);
        if(mappingPropUpdate != null) {
            doValidatePropertyUpdate(pEditorInfo, mappingPropUpdate);
        }
    }

    /**
     * Do the mapping validation on updated synonym mappings.
     */
    @Override
    public void validateUpdateAsset(AssetEditorInfo pEditorInfo, Collection pUpdates) {
        super.validateUpdateAsset(pEditorInfo, pUpdates);
        PropertyUpdate mappingPropUpdate = BaseAssetService.findPropertyUpdate(SynonymProperty.MAPPING, pUpdates);
        if(mappingPropUpdate != null) {
            doValidatePropertyUpdate(pEditorInfo, mappingPropUpdate);
        }
    }

    /**
     * Does the actual synonym mapping property validation.
     */
    protected void doValidatePropertyUpdate(AssetEditorInfo editorInfo, PropertyUpdate update) {
        String value = (String) update.getPropertyValue();

        if (value.indexOf(ARROW) != -1) {
            String[] words = StringUtils.split(value, ARROW);
            if (words == null || words.length != 2) {
                editorInfo.getAssetService().addError(update.getPropertyName(), ERROR_INVALID_EXPLICIT_SYNONYM_MAPPING);
            }
        } else {
            String[] words = StringUtils.split(value, SEPARATOR);
    
            if (words == null || words.length <= 1) {
                editorInfo.getAssetService().addError(update.getPropertyName(), ERROR_INVALID_SYNONYM_MAPPING);
            }
        }
    }
}

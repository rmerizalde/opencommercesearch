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
import org.opencommercesearch.RuleManager;
import org.opencommercesearch.repository.RankingRuleProperty;

import atg.remote.assetmanager.editor.model.PropertyUpdate;
import atg.remote.assetmanager.editor.service.AssetEditorInfo;
import atg.repository.RepositoryItem;

/**
 * Validate that if you are specifying the '|' operator in the attribute property
 * of a ranking rule, then this operator is only appears once in the expression
 */
public class RankingRuleAssetValidator extends DefaultRuleAssetValidator {

    private static final String TOO_MANY_PIPE_OPERATOR_ERROR_MSG = "A Ranking rule can't have more that one '|' operator";
    private static final String MISSING_SECOND_EXPRESSION_ERROR_MSG = "The '|' operator requires an expression after it";

    @Override
    public void doValidation(AssetEditorInfo editorInfo, Collection updates) {
        validateQuery(editorInfo, updates);
    }

    private void validateQuery(AssetEditorInfo editorInfo, Collection updates){
        
        PropertyUpdate attributeProperty  = BaseAssetService.findPropertyUpdate(RankingRuleProperty.ATTRIBUTE, updates);
        RepositoryItem currentItem = (RepositoryItem) editorInfo.getAssetWrapper().getAsset();
        
        if(attributeProperty != null ) {
            //update asset scenario
        	validate(editorInfo, (String) attributeProperty.getPropertyValue());
        } else {
        	//create asset scenario
        	validate(editorInfo, (String) currentItem.getPropertyValue(RankingRuleProperty.ATTRIBUTE));
        }

    }

	private void validate(AssetEditorInfo editorInfo, String attributeValue) {
		if( StringUtils.countMatches(attributeValue, RuleManager.RANKING_SEPARATOR) > 1) {
		    editorInfo.getAssetService().addError(RankingRuleProperty.ATTRIBUTE, TOO_MANY_PIPE_OPERATOR_ERROR_MSG);
		} else if( StringUtils.endsWith(attributeValue, RuleManager.RANKING_SEPARATOR)){
			editorInfo.getAssetService().addError(RankingRuleProperty.ATTRIBUTE, MISSING_SECOND_EXPRESSION_ERROR_MSG);
		}
	}
}

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
import java.util.Calendar;
import java.util.Collection;

import org.opencommercesearch.repository.RuleProperty;

import atg.nucleus.ServiceMap;
import atg.remote.assetmanager.editor.service.AssetEditorInfo;
import atg.repository.MutableRepositoryItem;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;

public class RuleTargetAssetService extends BaseAssetService {

    private static final String DEFAULT_RULE = "defaultRule";
    private ServiceMap validatorMap = new ServiceMap();

    /**
     * The default life span of created rules in days. After this period of time, rules should 
     * not affect search results at all.
     * TODO: Should this be more specific? i.e. minutes? would you ever want to create a rule for half an hour?
     * what about 1 hour? 
     */
    private static final int DEFAULT_RULE_LIFESPAN = 45;

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

                validator.setUserProfile(this.getUserProfile());

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

    /**
     * Sets the start and end date for the updated rule.
     */
    @Override
    public void preUpdateAsset(AssetEditorInfo pEditorInfo, Collection pUpdates) {
        super.preUpdateAsset(pEditorInfo, pUpdates);
        
        setStartAndEndDate(pEditorInfo);
    }

    /**
     * Sets the start and end date for the created rule.
     */
    @Override
    public void preAddAsset(AssetEditorInfo pEditorInfo, Collection pUpdates) {
        super.preAddAsset(pEditorInfo, pUpdates);

        setStartAndEndDate(pEditorInfo);
    }

    /**
     * Sets the proper start and end date for a created rule.
     * <p/>
     * If the rule does not have a start date, is assumed to be the current time.
     * On the other hand, if no end date was given the rule is assumed to end 
     * {@link #DEFAULT_RULE_LIFESPAN} days from the start date.
     * </p>
     * Examples: 
     * <br>
     * &nbsp;&nbsp;&nbsp;startdate=null and enddate=null -> Rule will expire in {@link #DEFAULT_RULE_LIFESPAN} days from now
     * <br>
     * &nbsp;&nbsp;&nbsp;startdate=null and enddate=30 days from today -> Rule will expire in 30 days from now.
     * <br>
     * &nbsp;&nbsp;&nbsp;startdate=tomorrow and enddate=null -> Rule will expire {@link #DEFAULT_RULE_LIFESPAN} days after tomorrow
     * 
     * @param pEditorInfo The asset editor info that contains an added/edited rule.
     */
    protected void setStartAndEndDate(AssetEditorInfo pEditorInfo) {
        //Get the current rule.
        MutableRepositoryItem currentRule = getItemForUpdate(pEditorInfo);

        //Check if the start date was given.
        Timestamp startDate = (Timestamp) currentRule.getPropertyValue(RuleProperty.START_DATE);
        if(startDate == null) {
            startDate = new Timestamp(System.currentTimeMillis());
        }

        //Check if the end date was given (this is mostly to ensure backwards compatibility - end date is now required on the
        //search repository descriptor). We don't want to insert empty dates from now on, even if is by mistake.
        Timestamp endDate = (Timestamp) currentRule.getPropertyValue(RuleProperty.END_DATE);
        if(endDate == null) {
            //Add DEFAULT_RULE_LIFESPAN days to the start date.
            Calendar cal = Calendar.getInstance();
            cal.setTime(startDate);
            cal.add(Calendar.DAY_OF_WEEK, DEFAULT_RULE_LIFESPAN);

            endDate = new Timestamp(cal.getTime().getTime());
        }
    }

    public ServiceMap getValidatorMap() {
        return validatorMap;
    }

    public void setValidatorMap(ServiceMap validatorMap) {
        this.validatorMap = validatorMap;
    }
}

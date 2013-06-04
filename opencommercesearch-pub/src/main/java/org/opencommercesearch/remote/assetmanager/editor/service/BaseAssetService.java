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

import org.opencommercesearch.repository.BaseAssetProperty;

import atg.adapter.secure.GenericSecuredRepositoryVersionItem;
import atg.remote.assetmanager.editor.model.AssetViewUpdate;
import atg.remote.assetmanager.editor.model.PropertyEditorAssetViewUpdate;
import atg.remote.assetmanager.editor.model.PropertyUpdate;
import atg.remote.assetmanager.editor.service.AssetEditorInfo;
import atg.remote.assetmanager.editor.service.RepositoryAssetServiceImpl;
import atg.repository.MutableRepositoryItem;
import atg.userprofiling.Profile;

/**
 * Generic asset service implementation. Used to auto-generate control/review values for assets
 * (such as creation date, modification date, creator, modifier, ...).
 * @author jmendez
 */
public class BaseAssetService extends RepositoryAssetServiceImpl {

    /**
     * Holds information about the user making the current request.
     */
    private Profile userProfile;

    /**
     * Sets sets last modification date and last modifier properties to a recently edited asset.
     */
    @Override
    public void preUpdateAsset(AssetEditorInfo pEditorInfo, Collection pUpdates) {
        super.preUpdateAsset(pEditorInfo, pUpdates);

        Object theAsset = pEditorInfo.getAssetWrapper().getAsset();
        MutableRepositoryItem currentItem = null;

        //Depending on the asset type, this could be a MutableRepositoryItem or GenericSecuredRepositoryVersionItem
        if(theAsset instanceof GenericSecuredRepositoryVersionItem) {
            GenericSecuredRepositoryVersionItem itemVersion = (GenericSecuredRepositoryVersionItem) theAsset;
            currentItem = (MutableRepositoryItem) itemVersion.getRepositoryItem();
        }
        else {
            currentItem = (MutableRepositoryItem) theAsset;
        }

        logError(currentItem == null? "null" : currentItem.getClass().toString());
        currentItem.setPropertyValue(BaseAssetProperty.LAST_MODIFIED_DATE, new Timestamp(System.currentTimeMillis()));
        currentItem.setPropertyValue(BaseAssetProperty.LAST_MODIFIED_BY, getUserProfile().getRepositoryId());
    }

    /**
     * Sets creation date and creator properties to a recently created asset.
     */
    @Override
    public void preAddAsset(AssetEditorInfo pEditorInfo, Collection pUpdates) {
        super.preAddAsset(pEditorInfo, pUpdates);
        //Get the current item being created
        MutableRepositoryItem currentItem = (MutableRepositoryItem) pEditorInfo.getAssetWrapper().getAsset();

        String currentUserId = getUserProfile().getRepositoryId();

        //Set control/review fields
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        currentItem.setPropertyValue(BaseAssetProperty.LAST_MODIFIED_DATE, timestamp);
        currentItem.setPropertyValue(BaseAssetProperty.LAST_MODIFIED_BY, currentUserId);
        currentItem.setPropertyValue(BaseAssetProperty.CREATION_DATE, timestamp);
        currentItem.setPropertyValue(BaseAssetProperty.CREATED_BY, currentUserId);
    }

    //Getters and setters, necessary for dynamic repository injection.

    public Profile getUserProfile() {
        return userProfile;
    }

    public void setUserProfile(Profile userProfile) {
        this.userProfile = userProfile;
    }
    
    /**
     * Utility method that finds the element that matches the given property name from an update collection.
     * @param propName The property name we are looking for.
     * @param updates The collection of updates to look into.
     * @return The element within the collection that matches the property name specified. Otherwise returns null.
     */
    protected static PropertyUpdate findPropertyUpdate(String propName, Collection updates) {
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

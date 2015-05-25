package org.opencommercesearch.client.impl;

import org.opencommercesearch.client.ProductContent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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

@JsonIgnoreProperties(ignoreUnknown = true)
public class DefaultProductContent implements ProductContent {

    private String id;
    private String site;
    private String bottomLine;
    private String description;
    private String feedTimestamp;

    public String getId() {
        return id;
    }

    public String getBottomLine() {
        return bottomLine;
    }

    public String getDescription() {
        return description;
    }

    public String getFeedTimestamp() {
        return feedTimestamp;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setBottomLine(String bottomLine) {
        this.bottomLine = bottomLine;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setFeedTimestamp(String feedTimestamp) {
        this.feedTimestamp = feedTimestamp;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

}

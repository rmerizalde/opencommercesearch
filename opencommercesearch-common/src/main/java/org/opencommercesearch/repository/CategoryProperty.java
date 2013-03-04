package org.opencommercesearch.repository;

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

public class CategoryProperty {
    private CategoryProperty() {

    }
    
    public static final String ITEM_DESCRIPTOR = "category";
    public static final String CHILD_CATEGORIES = "childCategories";
    public static final String PARENT_CATALOGS = "parentCatalogs";
    public static final String FIXED_PARENT_CATEGORIES = "fixedParentCategories";
    public static final String FIXED_CHILD_CATEGORIES = "fixedChildCategories";
    public static final String SEARCH_TOKENS = "searchTokens";
}

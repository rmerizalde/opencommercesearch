package org.opencommercesearch.model;

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

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;

import java.util.ArrayList;
import java.util.List;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class Category {

    @JsonProperty
    private List<String> catalogs;

    @JsonProperty
    private List<String> parentCategories;

    public List<String> getCatalogs() {
        return catalogs;
    }

    private List<String> getParentCategories() {
        return parentCategories;
    }

    public void addCatalog(String catalog) {
        if (catalogs == null) {
            catalogs = new ArrayList<String>(4);
        }
        catalogs.add(catalog);
    }

    public void addParentCategory(String parentCategory) {
        if (parentCategories == null) {
            parentCategories = new ArrayList<String>();
        }
        parentCategories.add(parentCategory);
    }
}

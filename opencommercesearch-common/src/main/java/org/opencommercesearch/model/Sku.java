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
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.map.annotate.JsonSerialize;

import java.util.HashSet;
import java.util.Set;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class Sku {

    @JsonProperty
    private String id;

    @JsonProperty
    private String season;

    @JsonProperty
    private String year;

    @JsonProperty
    private Image image;

    @JsonProperty
    private Set<Country> countries;

    @JsonProperty
    private boolean isPastSeason;

    @JsonProperty
    private String colorFamily;

    @JsonProperty
    private boolean isRetail;

    @JsonProperty
    private boolean isCloseout;

    @JsonProperty
    private Size size;

    @JsonIgnore
    private Set<String> categories;

    @JsonIgnore
    private Set<String> categoryNodes;

    @JsonIgnore
    private Set<String> categoryLeaves;

    @JsonIgnore
    private Set<String> ancestorCategories;

    @JsonIgnore
    private Set<String> categoryTokens;

    @JsonProperty
    private int customSort;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSeason() {
        return season;
    }

    public void setSeason(String season) {
        this.season = season;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public Image getImage() {
        return image;
    }

    public void setImage(Image image) {
        this.image = image;
    }

    public void setCountries(Set<Country> countries) {
        this.countries = countries;
    }

    public boolean isPastSeason() {
        return isPastSeason;
    }

    public void setPastSeason(boolean isPastSeason) {
        isPastSeason = isPastSeason;
    }

    public String getColorFamily() {
        return colorFamily;
    }

    public void setColorFamily(String colorFamily) {
        this.colorFamily = colorFamily;
    }

    public boolean isRetail() {
        return isRetail;
    }

    public void setRetail(boolean isRetail) {
        isRetail = isRetail;
    }

    public boolean isCloseout() {
        return isCloseout;
    }

    public void setCloseout(boolean isCloseout) {
        isCloseout = isCloseout;
    }

    public Size getSize() {
        return size;
    }

    public void setSize(Size size) {
        this.size = size;
    }

    public Set<Country> getCountries() {
        return countries;
    }

    public void addCountry(Country country) {
        if (countries == null) {
            countries = new HashSet<Country>();
        }
        countries.add(country);
    }

    public Country getCountry(String code) {
        if (code ==  null || countries == null) {
            return null;
        }

        for (Country country : getCountries()) {
            if (code.equals(country.getCode())) {
                return country;
            }
        }
        return null;
    }

    @JsonIgnore
    public Set<String> getCategories() {
        return categories;
    }

    public void addCategory(String category) {
        if (ancestorCategories == null) {
            ancestorCategories = new HashSet<String>();
        }
        ancestorCategories.add(category);
    }

    @JsonIgnore
    public Set<String> getCategoryNodes() {
        return categoryNodes;
    }

    public void addCategoryNode(String category) {
        if (categoryNodes == null) {
            categoryNodes = new HashSet<String>();
        }
        categoryNodes.add(category);
    }

    @JsonIgnore
    public Set<String> getCategoryLeaves() {
        return categoryLeaves;
    }

    public void addCategoryLeaf(String category) {
        if (categoryLeaves == null) {
            categoryLeaves = new HashSet<String>();
        }
        categoryLeaves.add(category);
    }

    @JsonIgnore
    public Set<String> getAncestorCategories() {
        return ancestorCategories;
    }

    public void addAncestorCategory(String category) {
        if (ancestorCategories == null) {
            ancestorCategories = new HashSet<String>();
        }
        ancestorCategories.add(category);
    }

    @JsonIgnore
    public Set<String> getCategoryTokens() {
        return categoryTokens;
    }

    public void addCategoryToken(String token) {
        if (categoryTokens == null) {
            categoryTokens = new HashSet<String>();
        }
        categoryTokens.add(token);
    }

    public void addCategoryPath(String path) {
        // @todo(api)
    }

    public int getCustomSort() {
        return customSort;
    }

    public void setCustomSort(int customSort) {
        this.customSort = customSort;
    }

}

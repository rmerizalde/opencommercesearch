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



import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class Product {
    @JsonProperty
    private String id;

    @JsonProperty
    private String title;

    @JsonProperty
    private String description;

    @JsonProperty
    private String shortDescription;

    @JsonProperty
    private Brand brand;

    @JsonProperty
    private String gender;

    @JsonProperty
    private String sizingChart;
    
    @JsonProperty
    private CustomerReview customerReviews;
    
    @JsonProperty
    private List<Image> detailImages;

    @JsonProperty
    private List<String> bulletPoints;

    @JsonProperty
    private List<Attribute> features;

    @JsonProperty
    private List<Attribute> attributes;

    @JsonProperty
    private int listRank;

    // has free gift by catalog
    @JsonProperty
    private Map<String, Boolean> hasFreeGift;

    @JsonProperty
    private boolean isPackage;

    @JsonProperty
    private boolean isOem;
    
    @JsonProperty
    private Set<Category> categories;

    @JsonProperty
    private List<Sku> skus;

    @JsonProperty
    private Date activationDate;

    public Date getActivationDate() {
        return activationDate;
    }

    public void setActivationDate(Date activationDate) { this.activationDate = activationDate; }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public Brand getBrand() {
        return brand;
    }

    public void setBrand(Brand brand) {
        this.brand = brand;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getSizingChart() {
        return sizingChart;
    }

    public void setSizingChart(String sizingChart) {
        this.sizingChart = sizingChart;
    }

    public CustomerReview getCustomerReviews() {
        return customerReviews;
    }

    public void setCustomerReviews(CustomerReview customerReviews) {
        this.customerReviews = customerReviews;
    }

    public List<Image> getDetailImages() {
        return detailImages;
    }

    public void setDetailImages(List<Image> detailImages) {
        this.detailImages = detailImages;
    }

    public List<String> getBulletPoints() {
        return bulletPoints;
    }

    public void addBulletPoint(String bulletPoint) {
        if (bulletPoints == null) {
            bulletPoints = new ArrayList<String>();
        }
        bulletPoints.add(bulletPoint);
    }

    public List<Attribute> getFeatures() {
        return features;
    }

    public void addFeature(String name, String value) {
        if (features == null) {
            features = new ArrayList<Attribute>();
        }
        features.add(new Attribute(name, value));
    }

    public List<Attribute> getAttributes() {
        return attributes;
    }

    public void addAttribute(String name, String value) {
        if (attributes == null) {
            attributes = new ArrayList<Attribute>();
        }
        attributes.add(new Attribute(name, value));
    }

    public int getListRank() {
        return listRank;
    }

    public void setListRank(int listRank) {
        this.listRank = listRank;
    }

    public Map<String, Boolean> getHasFreeGift() {
        return hasFreeGift;
    }

    public void setHasFreeGift(Map<String, Boolean> hasFreeGift) {
        this.hasFreeGift = hasFreeGift;
    }

    public boolean isPackage() {
        return isPackage;
    }

    public void setPackage(boolean isPackage) {
        this.isPackage = isPackage;
    }

    public boolean isOem() {
      return isOem;
    }

    public void setOem(boolean isOem) { this.isOem = isOem; }

    public Set<Category> getCategories() {
        return categories;
    }

    public void addCategory(String categoryId) {
        if (categories == null) {
            categories = new HashSet<Category>();
        }
        categories.add(new Category(categoryId));
    }

    public List<Sku> getSkus() {
        return skus;
    }

    public void addSku(Sku sku) {
        if (skus == null) {
            skus = new ArrayList<Sku>();
        }
        skus.add(sku);
    }
}

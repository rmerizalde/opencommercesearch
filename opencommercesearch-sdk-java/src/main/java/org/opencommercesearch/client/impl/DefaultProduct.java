package org.opencommercesearch.client.impl;

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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.opencommercesearch.client.Product;
import org.opencommercesearch.client.ProductSummary;

import java.util.*;

/**
 * Default product implementation.
 *
 * @author jmendez
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DefaultProduct implements Product {
  private String id;
  private String title;
  private String description;
  private String shortDescription;
  private Brand brand;
  private String gender;
  private String sizingChart;
  private CustomerReview customerReviews;
  private List<Image> detailImages;
  private List<String> bulletPoints;
  private List<Attribute> features;
  private List<Attribute> attributes;
  private Integer listRank;
  private Map<String, Boolean> hasFreeGift;
  private Boolean isOutOfStock;
  private Boolean isPackage;
  private Boolean isOem;
  private Set<Category> categories;
  private List<Sku> skus;
  private Date activationDate;
  private Availability.Status availabilityStatus;
  private ProductSummary summary;

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

  public void setBulletPoints(List<String> bulletPoints) {
    this.bulletPoints = bulletPoints;
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

  public Integer getListRank() {
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

  @JsonProperty("isOutOfStock")
  public Boolean getOutOfStock() {
    return isOutOfStock;
  }

  public void setIsOutOfStock(Boolean outOfStock) {
    this.isOutOfStock = outOfStock;
  }

  public Boolean getOem() {
    return isOem;
  }

  @JsonProperty("isOem")
  public void setOem(Boolean oem) {
    this.isOem = oem;
  }

  @JsonProperty("isPackage")
  public Boolean getPackage() {
    return isPackage;
  }

  /**
   * @deprecated As of release 0.5.0, replace by {@link #setPackage}
   * @param isPackage
   */
  @JsonIgnore
  public void setIsPackage(Boolean isPackage) {
    this.isPackage = isPackage;
  }

  public void setPackage(Boolean isPackage) {
      this.isPackage = isPackage;
    }

  public Set<Category> getCategories() {
    return categories;
  }

  public void setCategories(Set<Category> categories) {
    this.categories = categories;
  }

  public void addCategory(String categoryId) {
      if (categories == null) {
          categories = new HashSet<Category>();
      }
      categories.add(new Category(categoryId));
  }

  public void setAttributes(List<Attribute> attributes) {
    this.attributes = attributes;
  }

  public void setFeatures(List<Attribute> features) {
    this.features = features;
  }

  public List<Sku> getSkus() {
    return skus;
  }

  public void setSkus(List<Sku> skus) {
    this.skus = skus;
  }

  public void addSku(Sku sku) {
      if (skus == null) {
          skus = new ArrayList<Sku>();
      }
      skus.add(sku);
  }

  public Date getActivationDate() {
    return activationDate;
  }

  public void setActivationDate(Date activationDate) {
    this.activationDate = activationDate;
  }

  public Availability.Status getAvailabilityStatus() {
    return availabilityStatus;
  }

  public void setAvailabilityStatus(Availability.Status availabilityStatus) {
    this.availabilityStatus = availabilityStatus;
  }

  @JsonIgnore
  public ProductSummary getSummary() {
    return summary;
  }

  @JsonIgnore
  public void setSummary(ProductSummary summary) {
    this.summary = summary;
  }


}

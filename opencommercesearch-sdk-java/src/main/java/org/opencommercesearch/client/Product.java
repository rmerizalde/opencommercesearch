package org.opencommercesearch.client;

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
import org.opencommercesearch.client.ProductSummary;
import org.opencommercesearch.client.impl.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a product.
 *
 * @author jmendez
 */
public interface Product {
  public String getActivationDate();

  public String getId();

  public String getTitle();

  public String getDescription();

  public String getShortDescription();

  public Brand getBrand();

  public String getGender();

  public String getSizingChart();

  public CustomerReview getCustomerReviews();

  public List<Image> getDetailImages();

  public List<String> getBulletPoints();

  public List<Attribute> getFeatures();

  public List<Attribute> getAttributes();

  public int getListRank();

  public Map<String, Boolean> getHasFreeGift();

  public boolean isOutOfStock();

  public boolean isPackage();

  public boolean isOem();

  public Set<Category> getCategories();

  public List<Sku> getSkus();

  @JsonIgnore
  public ProductSummary getSummary();
}

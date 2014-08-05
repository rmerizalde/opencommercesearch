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
import org.opencommercesearch.client.impl.Attribute;
import org.opencommercesearch.client.impl.Availability;
import org.opencommercesearch.client.impl.Brand;
import org.opencommercesearch.client.impl.Category;
import org.opencommercesearch.client.impl.CustomerReview;
import org.opencommercesearch.client.impl.Image;
import org.opencommercesearch.client.impl.Sku;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a product.
 *
 * @author jmendez
 */
public interface Product {

  String getId();

  String getTitle();

  String getDescription();

  String getShortDescription();

  Brand getBrand();

  String getGender();

  String getSizingChart();

  CustomerReview getCustomerReviews();

  List<Image> getDetailImages();

  List<String> getBulletPoints();

  List<Attribute> getFeatures();

  List<Attribute> getAttributes();

  Integer getListRank();

  Map<String, Boolean> getHasFreeGift();

  Boolean getOutOfStock();

  Boolean getPackage();

  Boolean getOem();

  Set<Category> getCategories();

  List<Sku> getSkus();

  Date getActivationDate();
  
  Availability.Status getAvailabilityStatus();

  @JsonIgnore
  ProductSummary getSummary();
}

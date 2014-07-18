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
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

/**
 * Represents a product category.
 *
 * @author jmendez
 */
public class Category {
  private String id;
  private String name;
  private Category[] parentCategories;
  private Category[] childCategories;
  @JsonProperty("seoUrlToken")
  private String url;
  private boolean isRuleBased;
  private Set<String> sites;

  public Category() {}

  public Category(String id) { this.id = id; }

  public void setId(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Category[] getParentCategories() {
    return parentCategories;
  }

  public void setParentCategories(Category[] parentCategories) {
    this.parentCategories = parentCategories;
  }

  public Category[] getChildCategories() {
    return childCategories;
  }

  public void setChildCategories(Category[] childCategories) {
    this.childCategories = childCategories;
  }

  @JsonIgnore
  public boolean isRuleBased() {
    return isRuleBased;
  }

  public void setIsRuleBased(boolean isRuleBased) {
    this.isRuleBased = isRuleBased;
  }

  public String getUrl() { return url; }

  public void setUrl(String url) {
    if (url.startsWith("/")) {
      this.url = url;
    } else {
      this.url = "/" + url;
    }
  }

  public Set<String> getSites() {
    return sites;
  }

  public void setSites(Set<String> sites) {
    this.sites = sites;
  }
}

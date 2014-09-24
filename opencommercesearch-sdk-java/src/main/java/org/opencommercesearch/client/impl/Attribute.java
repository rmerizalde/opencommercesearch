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

/**
 * Represents a product attribute.
 *
 * @author jmendez
 * @author rmerizalde
 */
public class Attribute {
  private String name;
  private String value;
  private Boolean searchable;

  public Attribute() {}

  public Attribute(String name, String value) {
    this(name, value, null);
  }

  public Attribute(String name, String value, Boolean searchable) {
    this.name = name;
    this.value = value;
    this.searchable = searchable;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public Boolean getSearchable() {
    return searchable;
  }

  public void setSearchable(Boolean searchable) {
    this.searchable = searchable;
  }
}

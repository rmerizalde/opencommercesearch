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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Size data holder class.
 *
 * @author jmendez
 * @author rmerizalde
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Size {

  private String name;
  private String scale;
  private Size preferred;
  private Integer position;

  public Size() {}

  public Size(String name, String scale, Integer position) {
      this.name = name;
      this.scale = scale;
      this.position = position;
    }

  public Size(String name, String scale) {
    this.name = name;
    this.scale = scale;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getScale() {
    return scale;
  }

  public void setScale(String scale) {
    this.scale = scale;
  }

  public Size getPreferred() {
    return preferred;
  }

  public void setPreferred(Size preferred) {
    this.preferred = preferred;
  }

  public Integer getPosition() {
    return position;
  }

  public void setPosition(Integer position) {
    this.position = position;
  }
}

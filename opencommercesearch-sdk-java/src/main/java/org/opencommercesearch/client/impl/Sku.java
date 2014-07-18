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

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class Sku {
  private String id;
  private String title;
  private Image image;
  private Boolean isPastSeason;
  private Boolean isRetail;
  private Boolean isCloseout;
  private Boolean isOutlet;
  private Double listPrice;
  private Double salePrice;
  private Integer discountPercent;
  private String url;
  private Boolean allowBackorder;
  private Size size;
  private Color color;
  private Set<String> catalogs;
  private String year;
  private String season;
  private Availability availability;
  private Set<Country> countries;

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

  public Integer getDiscountPercent() {
      return discountPercent;
  }

  public void setDiscountPercent(Integer discountPercent) {
      this.discountPercent = discountPercent;
  }

  public Image getImage() {
      return image;
  }

  public void setImage(Image image) {
      this.image = image;
  }

  @JsonProperty("isPastSeason")
  public Boolean isPastSeason() {
      return isPastSeason;
  }

    /**
     * @deprecated As of release 0.5.0, replace by {@link #setPastSeason}
     * @param isPastSeason
     */
  @JsonIgnore
  public void setIsPastSeason(Boolean isPastSeason) {
      this.isPastSeason = isPastSeason;
  }

  public void setPastSeason(Boolean isPastSeason) {
          this.isPastSeason = isPastSeason;
      }

  @JsonProperty("isRetail")
  public Boolean isRetail() {
      return isRetail;
  }

  /**
   * @deprecated As of release 0.5.0, replace by {@link #setRetail}
   * @param isRetail
   */
  @JsonIgnore
  public void setIsRetail(Boolean isRetail) {
      this.isRetail = isRetail;
  }

  public void setRetail(Boolean isRetail) {
          this.isRetail = isRetail;
      }

  @JsonProperty("isCloseout")
  public Boolean isCloseout() {
      return isCloseout;
  }

  /**
   * @deprecated As of release 0.5.0, replace by {@link #setCloseout}
   * @param isCloseout
   */
  @JsonIgnore
  public void setIsCloseout(Boolean isCloseout) {
      this.isCloseout = isCloseout;
  }

  public void setCloseout(Boolean isCloseout) {
      this.isCloseout = isCloseout;
  }

  @JsonProperty("isOutlet")
  public Boolean isOutlet() {
      return isOutlet;
  }

  /**
   * @deprecated As of release 0.5.0, replace by {@link #setOutlet}
   * @param isOutlet
   */
  @JsonIgnore
  public void setIsOutlet(Boolean isOutlet) {
      this.isOutlet = isOutlet;
  }

  public void setOutlet(Boolean isOutlet) {
      this.isOutlet = isOutlet;
  }

  public Double getSalePrice() {
      return salePrice;
  }

  public void setSalePrice(Double salePrice) {
      this.salePrice = salePrice;
  }

  public Double getListPrice() {
      return listPrice;
  }

  public void setListPrice(Double listPrice) {
      this.listPrice = listPrice;
  }

  public String getUrl() {
      return url;
  }

  public void setUrl(String url) {
      this.url = url;
  }

  public Boolean isAllowBackorder() {
      return allowBackorder;
  }

  public void setAllowBackorder(boolean allowBackorder) {
      this.allowBackorder = allowBackorder;
  }

  public Size getSize() {
      return size;
  }

  public void setSize(Size size) {
      this.size = size;
  }

  public Color getColor() {
      return color;
  }

  public void setColor(Color color) {
      this.color = color;
  }

  public Set<String> getCatalogs() { return catalogs; }

  public void setCatalogs(Set<String> catalogs) { this.catalogs = catalogs; }

  public void addCatalog(String catalog) {
    if (catalogs == null) {
      catalogs = new HashSet<String>();
    }
    catalogs.add(catalog);
  }

  public String getYear() {
    return year;
  }

  public void setYear(String year) {
    this.year = year;
  }

  public String getSeason() {
    return season;
  }

  public void setSeason(String season) {
    this.season = season;
  }

  public Availability getAvailability() { return availability; }

  public void setAvailability(Availability availability) { this.availability = availability; }

  public Set<Country> getCountries() { return this.countries; }

  public void setCountries(Set<Country> countries) { this.countries = countries; }

  public void addCountry(Country country) {
    if (countries == null) {
        countries = new HashSet<Country>();
    }
    countries.add(country);
  }
}

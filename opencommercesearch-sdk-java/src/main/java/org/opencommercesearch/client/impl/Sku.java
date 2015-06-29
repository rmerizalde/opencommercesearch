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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
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
  private Long stockLevel;
  private List<Attribute> attributes;

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
  public Boolean getPastSeason() {
      return isPastSeason;
  }

    /**
     * @deprecated As of release 0.5.0, replace by {@link #setPastSeason}
     * @param pastSeason
     */
  @JsonIgnore
  public void setIsPastSeason(Boolean pastSeason) {
      this.isPastSeason = pastSeason;
  }

  public void setPastSeason(Boolean pastSeason) {
          this.isPastSeason = pastSeason;
      }

  @JsonProperty("isRetail")
  public Boolean getRetail() {
      return isRetail;
  }

  /**
   * @deprecated As of release 0.5.0, replace by {@link #setRetail}
   * @param retail
   */
  @JsonIgnore
  public void setIsRetail(Boolean retail) {
      this.isRetail = retail;
  }

  public void setRetail(Boolean retail) {
          this.isRetail = retail;
      }

  @JsonProperty("isCloseout")
  public Boolean getCloseout() {
      return isCloseout;
  }

  /**
   * @deprecated As of release 0.5.0, replace by {@link #setCloseout}
   * @param closeout
   */
  @JsonIgnore
  public void setIsCloseout(Boolean closeout) {
      this.isCloseout = closeout;
  }

  public void setCloseout(Boolean closeout) {
      this.isCloseout = closeout;
  }

  @JsonProperty("isOutlet")
  public Boolean getOutlet() {
      return isOutlet;
  }

  /**
   * @deprecated As of release 0.5.0, replace by {@link #setOutlet}
   * @param outlet
   */
  @JsonIgnore
  public void setIsOutlet(Boolean outlet) {
      this.isOutlet = outlet;
  }

  public void setOutlet(Boolean outlet) {
      this.isOutlet = outlet;
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

  public Boolean getAllowBackorder() {
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

  /**
   * @deprecated  As of release 0.5.0, replaced by {@link #getAvailability().}
   */
  public Long getStockLevel() {
    return stockLevel;
  }

  /**
   * @deprecated  As of release 0.5.0, replaced by {@link #getAvailability().}
   */
  public void setStockLevel(Long stockLevel) {
    this.stockLevel = stockLevel;
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
}

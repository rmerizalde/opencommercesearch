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

import java.util.Date;
import java.util.Set;

public class Sku {
  private String id;
  private String title;
  private Image image;
  private boolean isPastSeason;
  private boolean isRetail;
  private boolean isCloseout;
  private boolean isOutlet;
  private double listPrice;
  private double salePrice;
  private int discountPercent;
  private String url;
  private int stockLevel;
  private boolean allowBackorder;
  private Size size;
  private Color color;
  private Set<String> catalogs;
  private String year;
  private String season;
  private Date availableDate;

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

  public int getDiscountPercent() {
      return discountPercent;
  }

  public void setDiscountPercent(int discountPercent) {
      this.discountPercent = discountPercent;
  }

  public Image getImage() {
      return image;
  }

  public void setImage(Image image) {
      this.image = image;
  }

  public boolean isPastSeason() {
      return isPastSeason;
  }

  public void setIsPastSeason(boolean isPastSeason) {
      this.isPastSeason = isPastSeason;
  }

  public boolean isRetail() {
      return isRetail;
  }

  public void setIsRetail(boolean isRetail) {
      this.isRetail = isRetail;
  }

  public boolean isCloseout() {
      return isCloseout;
  }

  public void setIsCloseout(boolean isCloseout) {
      this.isCloseout = isCloseout;
  }

  public boolean isOutlet() {
      return isOutlet;
  }

  public void setIsOutlet(boolean isOutlet) {
      this.isOutlet = isOutlet;
  }

  public double getSalePrice() {
      return salePrice;
  }

  public void setSalePrice(double salePrice) {
      this.salePrice = salePrice;
  }

  public double getListPrice() {
      return listPrice;
  }

  public void setListPrice(double listPrice) {
      this.listPrice = listPrice;
  }

  public String getUrl() {
      return url;
  }

  public void setUrl(String url) {
      this.url = url;
  }

  public int getStockLevel() {
      return stockLevel;
  }

  public void setStockLevel(int stockLevel) {
      this.stockLevel = stockLevel;
  }

  public boolean isAllowBackorder() {
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

  public Date getAvailableDate() { return availableDate; }

  public void setAvailableDate(Date availableDate) { this.availableDate = availableDate; }
}

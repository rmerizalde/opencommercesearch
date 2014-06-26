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

import java.util.Locale;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class Country {
    @JsonProperty
    private String code;

    @JsonProperty
    private double listPrice;

    @JsonProperty
    private double salePrice;

    @JsonProperty
    private int discountPercent;

    @JsonProperty
    private long stockLevel;

    @JsonProperty
    private String url;

    @JsonProperty
    private boolean allowBackorder;

    public Country(String code) {
        this.code = code;
    }

    public Country(Locale locale) {
        this.code = locale.getCountry();
    }

    public int hashCode() {
        return code.hashCode();
    }

    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (anObject instanceof Country) {
            return code.equals(((Country) anObject).code);
        }
        return false;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public double getListPrice() {
        return listPrice;
    }

    public void setListPrice(double listPrice) {
        this.listPrice = listPrice;
    }

    public double getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(double salePrice) {
        this.salePrice = salePrice;
    }

    public int getDiscountPercent() {
        return discountPercent;
    }

    public boolean getOnSale() {
        return discountPercent > 0;
    }

    public void setDiscountPercent(int discountPercent) {
        this.discountPercent = discountPercent;
    }

    public long getStockLevel() {
        return stockLevel;
    }

    public void setStockLevel(long stockLevel) {
        this.stockLevel = stockLevel;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isAllowBackorder() {
        return allowBackorder;
    }

    public void setAllowBackorder(boolean allowBackorder) {
        this.allowBackorder = allowBackorder;
    }
}

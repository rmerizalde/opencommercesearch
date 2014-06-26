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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class Sku {

    @JsonProperty
    private String id;

    @JsonProperty
    private String title;

    @JsonProperty
    private String season;

    @JsonProperty
    private String year;

    @JsonProperty
    private Image image;

    @JsonProperty
    private Color color;
    
    @JsonProperty
    private Set<Country> countries;

    @JsonProperty
    private boolean isPastSeason;

    @JsonProperty
    private boolean isRetail;

    @JsonProperty
    private boolean isCloseout;

    @JsonProperty
    private boolean isOutlet;

    @JsonProperty
    private Size size;

    @JsonProperty
    private Set<String> catalogs;

    @JsonIgnore
    private boolean isAssigned;

    @JsonProperty
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
    
    public String getSeason() {
        return season;
    }

    public void setSeason(String season) {
        this.season = season;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public Image getImage() {
        return image;
    }

    public void setImage(Image image) {
        this.image = image;
    }

    public void setCountries(Set<Country> countries) {
        this.countries = countries;
    }

    @JsonIgnore
    public boolean isPastSeason() {
        return isPastSeason;
    }

    public void setPastSeason(boolean isPastSeason) {
        this.isPastSeason = isPastSeason;
    }
    
    @JsonIgnore
    public boolean isRetail() {
        return isRetail;
    }

    public void setRetail(boolean isRetail) {
        this.isRetail = isRetail;
    }

    @JsonIgnore
    public boolean isCloseout() {
        return isCloseout;
    }

    public void setCloseout(boolean isCloseout) {
        this.isCloseout = isCloseout;
    }
    
    @JsonIgnore
    public boolean isOutlet() {
        return isOutlet;
    }

    public void setOutlet(boolean isOutlet) {
        this.isOutlet = isOutlet;
    }

    public Size getSize() {
        return size;
    }

    public void setSize(Size size) {
        this.size = size;
    }

    public Set<Country> getCountries() {
        return countries;
    }

    public void addCountry(Country country) {
        if (countries == null) {
            countries = new HashSet<Country>();
        }
        countries.add(country);
    }

    public Country getCountry(String code) {
        if (code ==  null || countries == null) {
            return null;
        }

        for (Country country : getCountries()) {
            if (code.equals(country.getCode())) {
                return country;
            }
        }
        return null;
    }

    @JsonIgnore
    public Set<String> getCatalogs() {
        return catalogs;
    }

    public void addCatalog(String catalog) {
        if (catalogs == null) {
            catalogs = new HashSet<String>();
        }
        catalogs.add(catalog);
    }

    @JsonIgnore
    public boolean isAssigned() {
        return isAssigned;
    }

    public void setAssigned(boolean isAssigned) {
        this.isAssigned = isAssigned;
    }
    
    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public Date getAvailableDate() { return availableDate; }

    public void setAvailableDate(Date availableDate) { this.availableDate = availableDate; }
}

package org.opencommercesearch.model;

import org.codehaus.jackson.annotate.JsonProperty;

public class ColorInfo {
    
    @JsonProperty
    private String colorFamily;

    @JsonProperty
    private String color;

    public String getColorFamily() {
        return colorFamily;
    }

    public void setColorFamily(String colorFamily) {
        this.colorFamily = colorFamily;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
    
}

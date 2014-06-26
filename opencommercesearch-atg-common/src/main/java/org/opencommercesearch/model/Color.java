package org.opencommercesearch.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Color {
    
    @JsonProperty
    private String name;

    @JsonProperty
    private String family;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }
    
}

package org.opencommercesearch.client.response;

import org.opencommercesearch.client.impl.Category;
import org.opencommercesearch.client.impl.Metadata;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Simple data holder that represents a response from category endpoint
 * 
 * @author gsegura@backcountry.com
 */
public class CategoryResponse extends DefaultResponse {
    
    private Metadata metadata;
    private Category[] categories;
    
    public Metadata getMetadata() {
        return metadata;
    }
    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }
    
    /**
     * @deprecated api will return an array of categories soon
     * @param category
     */
    @JsonProperty("category")
    public void setCategory(Category category) {
        this.categories = new Category[] { category };
    }
	public Category[] getCategories() {
		return categories;
	}
	public void setCategories(Category[] categories) {
		this.categories = categories;
	}
}

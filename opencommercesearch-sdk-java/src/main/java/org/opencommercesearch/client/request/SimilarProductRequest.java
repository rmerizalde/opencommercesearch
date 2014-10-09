package org.opencommercesearch.client.request;

import org.opencommercesearch.client.ProductApi;

/**
 * A request to retrieve similar products by id
 * 
 * @author nkumar
 */
public class SimilarProductRequest extends BaseRequest {

	private String endpoint;

	public SimilarProductRequest(String id) {
		endpoint = "/products/" + id + "/similar";
	}

	@Override
	public String getEndpoint() {
		return endpoint;
	}

	@Override
	public ProductApi.RequestMethod getMethod() {
		return ProductApi.RequestMethod.GET;
	}

}

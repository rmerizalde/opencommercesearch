package org.opencommercesearch.client.response;

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

import com.fasterxml.jackson.databind.JsonNode;
import org.opencommercesearch.client.Product;
import org.opencommercesearch.client.impl.DefaultProduct;
import org.opencommercesearch.client.impl.DefaultProductSummary;
import org.opencommercesearch.client.impl.Metadata;

/**
 * Simple data holder that represents a response from search endpoints.
 *
 * @author jmendez
 */
public class SearchResponse extends DefaultResponse {
    private Metadata metadata;
    private DefaultProduct[] products;
    private boolean boundSummaries = false;

    public Metadata getMetadata() {
        return metadata;
    }

    protected void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public Product[] getProducts() {
        if (!boundSummaries) {
            bindSummaries();
        }

        return products;

    }

    protected void setProducts(DefaultProduct[] products) {
        this.products = products;
    }

    /**
     * Adds to each product in the response, its corresponding product summary information.
     */
    private void bindSummaries() {
        JsonNode summaries = metadata.getProductSummary();

        if (products != null) {
            for (DefaultProduct product : products) {
                DefaultProductSummary productSummary = new DefaultProductSummary(summaries.get(product.getId()));
                product.setSummary(productSummary);
            }
        }
    }
}

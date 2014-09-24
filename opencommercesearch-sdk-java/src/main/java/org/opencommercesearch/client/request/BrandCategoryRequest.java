package org.opencommercesearch.client.request;

import org.opencommercesearch.client.ProductApi;
import org.opencommercesearch.client.ProductApi.RequestMethod;

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

/**
 * A request to retrieve the categories from a given brand
 *
 * @author gsegura@backcountry.com
 */
public class BrandCategoryRequest extends BaseRequest {

    private String brandId;
    
    public BrandCategoryRequest(String brandId) {
        this.brandId = brandId;
    }
    
    public String getBrandId() {
        return brandId;
    }

    @Override
    public String getEndpoint() {
        return "/brands/" + brandId + "/categories";
    }

    @Override
    public RequestMethod getMethod() {
        return ProductApi.RequestMethod.GET;
    }

}

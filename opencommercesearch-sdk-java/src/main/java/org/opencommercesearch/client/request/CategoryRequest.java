package org.opencommercesearch.client.request;

import org.apache.commons.lang.StringUtils;
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
 * A request to retrieve one category by id
 *
 * @author gsegura@backcountry.com
 */
public class CategoryRequest extends BaseRequest {

    private String categoryId;
    
    public CategoryRequest() {
        
    }
            
    public CategoryRequest(String categoryId){
        this.categoryId = categoryId;
    }
    
    public String getCategoryId() {
        return categoryId;
    }
    
    public void setMaxLevels(int maxLevels) {
        setParam("maxLevels", String.valueOf(maxLevels));
    }

    public void setMaxChildren(int maxChildren) {
        setParam("maxChildren", String.valueOf(maxChildren));
    }
      
    public void setOutlet(boolean outlet) {
        setParam("outlet", String.valueOf(outlet));
    }

    public void addFilterQuery(String filterQuery) {
        addParam("filterQueries", filterQuery, FILTER_QUERY_SEPARATOR);
    }
    
    public void setFilterQueries(String[] filterQueries) {
        if (filterQueries == null) {
          throw new NullPointerException("filterQueries");
        }
    
        setParam("filterQueries", StringUtils.join(filterQueries, FILTER_QUERY_SEPARATOR));
    }
    
    @Override
    public String getEndpoint() {
        if(categoryId != null) {
            return "/categories/" + categoryId;
        } else {
            return "/categories" ;
        }
    }

    @Override
    public RequestMethod getMethod() {
        return ProductApi.RequestMethod.GET;
    }

}

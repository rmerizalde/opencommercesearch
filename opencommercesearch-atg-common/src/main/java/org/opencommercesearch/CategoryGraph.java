package org.opencommercesearch;

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

import java.util.List;


public class CategoryGraph implements Comparable<CategoryGraph> {

    private List<CategoryGraph> categoryGraphNodes;
    private int count;
    private String path;
    private String id;
    
    public List<CategoryGraph> getCategoryGraphNodes() {
        return categoryGraphNodes;
    }
    public void setCategoryGraphNodes(List<CategoryGraph> categoryGraphNodes) {
        this.categoryGraphNodes = categoryGraphNodes;
    }
    public int getCount() {
        return count;
    }
    public void setCount(int count) {
        this.count = count;
    }
    public String getPath() {
        return path;
    }
    public void setPath(String path) {
        this.path = path;
    }
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    @Override
    public int compareTo(CategoryGraph o) {
        return id.compareTo(o.getId());
    }
    @Override
    public String toString() {
        return "CategoryGraph [count=" + count + ", path=" + path + ", id="
                + id + "]";
    }

}

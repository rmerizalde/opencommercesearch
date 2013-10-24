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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.opencommercesearch.Facet.Filter;

import com.google.common.collect.Lists;

public class CategoryGraphBuilder {

    private static final String CATEGORY_PATH = "categoryPath";
    
    private CategoryGraph parentNode = new CategoryGraph();    
    
    public CategoryGraph getParentNode() {
        return parentNode;
    }

    public void setParentVO(CategoryGraph parentNode) {
        this.parentNode = parentNode;
    }

    public CategoryGraphBuilder() {
        List<CategoryGraph> childList = Lists.newArrayList();
        parentNode.setCategoryGraphNodes(childList);
    }
    
    public List<CategoryGraph> getCategoryGraphList() {
        return parentNode.getCategoryGraphNodes();
    }
    
    public void addPath(Filter filter){
        String filterPath = Utils.findFilterExpressionByName(filter.getPath(), CATEGORY_PATH);
        if(filterPath != null) {
            String[] pathArray = StringUtils.split(filterPath, SearchConstants.CATEGORY_SEPARATOR);
            parentNode.setId(pathArray[0]);
            recursiveAdd(filter, pathArray, 1, parentNode);
        }  
    }

    private void recursiveAdd(Filter filter, String[] pathArray, int arrayIndex, CategoryGraph parentNode) {
        
        if(pathArray == null || pathArray.length == 0){
            return;
        }
        
        int currentOffset = pathArray.length - arrayIndex;
        if(currentOffset < 1) {
            return;
        }
        
        if(currentOffset == 1){
            createNewNode(filter, parentNode);
        } else {
            
            String currentId = pathArray[arrayIndex];
            CategoryGraph node = search(currentId, parentNode);
            
            if (node == null) {
                node = new CategoryGraph();
                List<CategoryGraph> childList = Lists.newArrayList();
                node.setCategoryGraphNodes(childList);
                node.setId(currentId);
                parentNode.getCategoryGraphNodes().add(node);
            }
            
            recursiveAdd(filter, pathArray, arrayIndex+1, node);
        }

    }
    
    private void createNewNode(Filter filter, CategoryGraph parentNode) {
        
        CategoryGraph node = search(filter.getName(), parentNode);
        List<CategoryGraph> parentChildList = parentNode.getCategoryGraphNodes();
        
        if (node == null) {                
            node = new CategoryGraph();
            List<CategoryGraph> childList = Lists.newArrayList();
            node.setCategoryGraphNodes(childList);
            parentChildList.add(node);
        }
        
        node.setCount((int) filter.getCount());
        node.setPath(filter.getPath());
        String name = filter.getName();
        int lastDotIndex = name.lastIndexOf('.');
        if (lastDotIndex != -1) {
            name = name.substring(lastDotIndex+1);
        }
        node.setId(name);
        Collections.sort(parentChildList);
    }
    
    public CategoryGraph search(String id, CategoryGraph graphNode){
        
        if (id.equals(graphNode.getId())) {
            return graphNode;
        }
        
        CategoryGraph result = null;
        for (CategoryGraph childFacet : graphNode.getCategoryGraphNodes()) {
            result = search(id, childFacet);
            if (result != null) {
                break;
            }
        }
        return result;
    }
    
}

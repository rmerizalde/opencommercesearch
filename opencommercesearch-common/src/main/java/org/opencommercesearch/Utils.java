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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.opencommercesearch.repository.CategoryProperty;

import atg.repository.RepositoryItem;

public class Utils {

    public static final String PATH_SEPARATOR = "|";
    public static final String RESOURCE_IN_RANGE = "inrange";
    public static final String RESOURCE_BEFORE = "before";
    public static final String RESOURCE_AFTER = "after";
    public static final String RESOURCE_CRUMB = "crumb";

    public static final ResourceBundle resources = ResourceBundle.getBundle("org.opencommercesearch.CSResources");

    public static String createPath(FilterQuery[] filterQueries, FilterQuery skipFilter) {
        return createPath(filterQueries, skipFilter, null);
    }

    public static String createPath(FilterQuery[] filterQueries, FilterQuery skipFilter, String replacementFilterQuery) {
        if (filterQueries == null) {
            return StringUtils.EMPTY;
        }

        StringBuffer b = new StringBuffer();

        for (FilterQuery filterQuery : filterQueries) {
            if (!filterQuery.equals(skipFilter)) {
                b.append(filterQuery.toString()).append(PATH_SEPARATOR);
            } else if (replacementFilterQuery != null) {
                b.append(replacementFilterQuery).append(PATH_SEPARATOR);
            }
        }
        if (b.length() > 0) {
            b.setLength(b.length() - 1);
        }
        return b.toString();
    }

    private static String loadResource(String key) {
        try {
            return resources.getString(key);
        } catch (MissingResourceException ex) {
            return null;
        }
    }

    public static String getRangeName(String fieldName, String key, String value1, String value2, String defaultName) {
        String resource = null;
        String resourceKey = "facet.range." + fieldName + "." + key;

        // First try to find if there's a specific resource for the value
        resource = loadResource(resourceKey + "." + value1);
        if (resource == null) {
            resource = loadResource(resourceKey + "." + value2);
        }
        if (resource == null) {
            resource = loadResource(resourceKey);
        }

        if (resource == null) {
            if (defaultName != null) {
                return defaultName;
            }
            resource = "${v1}-${v2}";
        }

        String rangeName = StringUtils.replace(resource, "${v1}", (value1 == null ? "" : value1));
        rangeName = StringUtils.replace(rangeName, "${v2}", (value2 == null ? "" : value2));
        return rangeName;
    }

    public static String getRangeName(String fieldName, String expression) {
        if (expression.startsWith("[") && expression.endsWith("]")) {
            String[] parts = StringUtils.split(expression.substring(1, expression.length() - 1), " TO ");
            if (parts.length == 2) {
                String key = Utils.RESOURCE_IN_RANGE;
                if ("*".equals(parts[0])) {
                    key = Utils.RESOURCE_BEFORE;
                } else if ("*".equals(parts[1])) {
                    key = Utils.RESOURCE_AFTER;
                }
                return Utils.getRangeName(fieldName, key, parts[0], parts[1], null);
            }
        }
        return expression;
    }

    public static String getRangeBreadCrumb(String fieldName, String expression)
    {
        return getRangeBreadCrumb(fieldName, expression, null);
    }

    public static String getRangeBreadCrumb(String fieldName, String expression, String defaultCrumb) {
        if (expression.startsWith("[") && expression.endsWith("]")) {
            String[] parts = StringUtils.split(expression.substring(1, expression.length() - 1), " TO ");
            if (parts.length == 2) {
                return getRangeName(fieldName, Utils.RESOURCE_CRUMB, parts[0], parts[1], defaultCrumb);
            }
        }
        return expression;
    }
    
    public static String findFilterExpressionByName(String path, String filterQueryName){
        String filterExpression = null;
        FilterQuery[] filterQueries = FilterQuery.parseFilterQueries(path);
        for(FilterQuery filterQuery :filterQueries) {
            if(filterQueryName.equals(filterQuery.getFieldName())){
                filterExpression = filterQuery.getExpression();
            }
        }
        
        return filterExpression;
    }
    
    /**
     * Builds the taxonomy paths for a given category. It drills up to the root category. The output format is:
     * "catalogId"."catLevel1"."catLevel2"..."catLevelN"
     * There can be multiple paths from a category to the catalog. All paths are calculated 
     * @param categoryItem The current category in the ladder up to the catalog
     * @param categoryPath The current taxonomy path being build 
     * @param categoryPaths stores the paths once a path reaches to the catalog 
     * @return
     */
    private static void buildAllCategoryPaths(RepositoryItem categoryItem, List<String> categoryPath, Set<String> categoryPaths){
        //check for parent catalogs
        Set<RepositoryItem> parentCatalogs = (Set<RepositoryItem>)categoryItem.getPropertyValue(CategoryProperty.PARENT_CATALOGS);
        if(parentCatalogs != null) {
            if (parentCatalogs.size() > 0) {
                for(RepositoryItem parentCatalog : parentCatalogs) {
                    categoryPath.add(parentCatalog.getRepositoryId());                
                    categoryPaths.add(buildPath(categoryPath));
                    categoryPath.remove(categoryPath.size()-1);
                }
            }
        }
        //check for fixedparent categories
        Set<RepositoryItem> fixedParentCatagories = (Set<RepositoryItem>)categoryItem.getPropertyValue(CategoryProperty.FIXED_PARENT_CATEGORIES);
        if (fixedParentCatagories != null) {
            for(RepositoryItem catagory : fixedParentCatagories) {
                if(!categoryPath.contains(catagory.getRepositoryId())) {
                    categoryPath.add(catagory.getRepositoryId());
                    buildAllCategoryPaths(catagory, categoryPath, categoryPaths);
                    categoryPath.remove(categoryPath.size()-1);
                }
            }
        }
    }
    
    /**
     * Auxiliary recursive method for buildCategoryPrefix.
     * @see buildCategoryPrefix
     * @param categoryPath LinkList storing the path
     * @return
     */
    private static String buildPath(List<String> categoryPath){
        StringBuilder prefix = new StringBuilder();
        for (int level = categoryPath.size() - 1 ; level >= 0; level --) {
            //from catagory4->category3->category->2->category1->category0->catalog
        	//remove category0
        	if(level != categoryPath.size() - 2) {
                prefix.append(categoryPath.get(level));
                if (level != 0) {
                    prefix.append(".");
                }
            }
        }
        return prefix.toString();
    }
    
    /**
     * Builds the taxonomy path for a given category. It drills up to the root category. The output format is:
     * "catalogId"."catLevel1"."catLevel2"..."catLevelN"
     * There can be multiple paths from a category to the catalog. All paths are calculated 
     * @param categoryItem The category we want to generate the taxonomy path for.
     * @return
     */
    public static Set<String> buildCategoryPrefix(RepositoryItem categoryItem){
        Set<String> categoryPaths = new HashSet<String>();
        List<String> categoryPath = new LinkedList<String>();
        categoryPath.add(categoryItem.getRepositoryId());
        buildAllCategoryPaths(categoryItem, categoryPath, categoryPaths);
        return categoryPaths;
    }

    /**
     * Builds the taxonomy path for a given category. It drills up to the root category. The output format is:
     * "catalogId"."catLevel1"."catLevel2"..."catLevelN"
     * 
     * @param catalogId The current catalog id 
     * @param categoryItem The category we want to generate the taxonomy path for.
     * @return
     */
    public static String buildCategoryPrefix(String catalogId, RepositoryItem categoryItem){
        StringBuilder prefix = new StringBuilder();
        
        List<String> path = new ArrayList<String>(); 
 
        if (categoryItem != null) {
            path.add(categoryItem.getRepositoryId());
            buildPath(path, categoryItem);               
        }
        
        if (path.size() > 0) {
            path.remove(0);
            
            prefix.append(catalogId);
            
            for (String entry : path) {
                prefix.append(".");
                prefix.append(entry);
            }
        }
        return prefix.toString(); 
        
    }
    
    /**
     * Auxiliary recursive method for buildCategoryPrefix.
     * 
     * @see buildCategoryPrefix
     * @param currentPath Placeholder to accumulate the path
     * @param category The current category we are traversing
     */
    private static void buildPath(List<String> currentPath, RepositoryItem category){
        @SuppressWarnings("unchecked")
        Set<RepositoryItem> parents = (Set<RepositoryItem>) category.getPropertyValue(CategoryProperty.FIXED_PARENT_CATEGORIES);        
        RepositoryItem parent = parents.iterator().next();
        if(parent != null){
            currentPath.add(0, parent.getRepositoryId());
            buildPath(currentPath, parent);
        }

    }
    
    
    
}

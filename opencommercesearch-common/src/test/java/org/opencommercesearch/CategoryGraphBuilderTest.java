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

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.opencommercesearch.Facet.Filter;


public class CategoryGraphBuilderTest {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testCategoryGraphBuilder() {
        
        CategoryGraphBuilder  builder = new CategoryGraphBuilder();
        
        
        builder.addPath(newFilter("level1", "root.level1"));
        builder.addPath(newFilter("node1-1", "root.level1.node1-1"));
        builder.addPath(newFilter("node1-2", "root.level1.node1-2"));
        builder.addPath(newFilter("level2", "root.level2"));
        builder.addPath(newFilter("node2-1", "root.level2.node2-1"));
        builder.addPath(newFilter("node2-1-1", "root.level2.node2-1.node2-1-1"));
        builder.addPath(newFilter("node2-1-2", "root.level2.node2-1.node2-1-2"));
        builder.addPath(newFilter("level3", "root.level3"));
        builder.addPath(newFilter("level4", "root.level4"));
        
        List<CategoryGraph> vos = builder.getCategoryGraphList();
        
        validate(vos);
       
    }

    @Test
    public void testCategoryGraphBuilderRandomOrder() {
        
        CategoryGraphBuilder  builder = new CategoryGraphBuilder();
        
        builder.addPath(newFilter("node1-2", "root.level1.node1-2"));
        builder.addPath(newFilter("level1", "root.level1"));
        builder.addPath(newFilter("node2-1-2", "root.level2.node2-1.node2-1-2"));
        builder.addPath(newFilter("node1-1", "root.level1.node1-1"));
        builder.addPath(newFilter("level3", "root.level3"));
        builder.addPath(newFilter("level2", "root.level2"));
        builder.addPath(newFilter("node2-1", "root.level2.node2-1"));
        builder.addPath(newFilter("node2-1-1", "root.level2.node2-1.node2-1-1"));
        builder.addPath(newFilter("level4", "root.level4"));
        
        List<CategoryGraph> vos = builder.getCategoryGraphList();
        
        validate(vos);
       
    }
    
    protected void validate(List<CategoryGraph> vos) {
        assertEquals("level1", vos.get(0).getId());
        assertEquals("node1-1", vos.get(0).getCategoryGraphNodes().get(0).getId());
        assertEquals("node1-2", vos.get(0).getCategoryGraphNodes().get(1).getId());
        assertEquals("level2", vos.get(1).getId());
        assertEquals("node2-1", vos.get(1).getCategoryGraphNodes().get(0).getId());
        assertEquals("node2-1-1", vos.get(1).getCategoryGraphNodes().get(0).getCategoryGraphNodes().get(0).getId());
        assertEquals("node2-1-2", vos.get(1).getCategoryGraphNodes().get(0).getCategoryGraphNodes().get(1).getId());
        assertEquals("level3", vos.get(2).getId());
        assertEquals("level4", vos.get(3).getId());
    }
    
    private Filter newFilter(String name, String path){
        Filter filter = new Filter();
        filter.setCount(1);
        filter.setName(name);
        filter.setPath("categoryPath:"+path);
        return filter;
    }

}

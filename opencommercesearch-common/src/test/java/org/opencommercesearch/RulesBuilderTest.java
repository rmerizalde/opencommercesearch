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
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.opencommercesearch.RulesTestUtil.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.solr.client.solrj.util.ClientUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opencommercesearch.repository.CategoryProperty;

import atg.repository.Repository;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryItemDescriptor;

public class RulesBuilderTest {

    RulesBuilder rulesBuilder = new RulesBuilder();

    @Mock
    Repository productCatalog;

    @Mock
    RepositoryItem category;
    
    @Before
    public void setUp() throws Exception {
        initMocks(this);
        rulesBuilder.setProductCatalog(productCatalog);
        when(category.getPropertyValue(CategoryProperty.SEARCH_TOKENS)).thenReturn("1.catalog.categoryToken");
        RepositoryItemDescriptor categoryItemName = mock(RepositoryItemDescriptor.class);
        when(categoryItemName.getItemDescriptorName()).thenReturn("category");
        when(category.getItemDescriptor()).thenReturn(categoryItemName );
    }

    @Test
    public void testBuildNoRules() throws RepositoryException {

        mockBaseRule(productCatalog, null, mock(RepositoryItem.class), "ruleCategory", true);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.ENGLISH);

        assertEquals("(ancestorCategoryId:ruleCategory)", builder);
    }

    @Test
    public void testBuildSimpleBrandRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("brand", 1, "88", null));
        mockBaseRule(productCatalog, expresionList, mock(RepositoryItem.class), "ruleCategory", true);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.ENGLISH);

        assertEquals("(ancestorCategoryId:ruleCategory) OR (brandId:88)", builder);
    }

    @Test
    public void testBuildSimplePorcentageOffRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("pct_off", 1, "15", null));
        mockBaseRule(productCatalog, expresionList, mock(RepositoryItem.class), "ruleCategory", true);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.US);

        assertEquals("(ancestorCategoryId:ruleCategory) OR (discountPercentUS:[15 TO 100])", builder);
    }

    @Test
    public void testBuildSimpleCategoryRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("category", 1, "cat1", null));
        mockBaseRule(productCatalog, expresionList, mock(RepositoryItem.class), "ruleCategory", true);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.US);

        assertEquals("(ancestorCategoryId:ruleCategory) OR (ancestorCategoryId:cat1)", builder);
    }

    @Test
    public void testBuildSimpleGenderRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("gender", 1, "male", null));
        mockBaseRule(productCatalog, expresionList, mock(RepositoryItem.class), "ruleCategory", true);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.US);

        assertEquals("(ancestorCategoryId:ruleCategory) OR (gender:male)", builder);
    }

    @Test
    public void testBuildSimpleShowSaleRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("show_sale", 1, "false", null));
        mockBaseRule(productCatalog, expresionList, mock(RepositoryItem.class), "ruleCategory", true);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.US);

        assertEquals("(ancestorCategoryId:ruleCategory) OR (onsaleUS:false)", builder);
    }

    @Test
    public void testBuildSimplePastSeasonRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("past_season", 1, "false", null));
        mockBaseRule(productCatalog, expresionList, mock(RepositoryItem.class), "ruleCategory", true);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.US);

        assertEquals("(ancestorCategoryId:ruleCategory) OR (isPastSeason:false)", builder);
    }

    @Test
    public void testBuildSimplePriceRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("price", 1, "25 100", null));
        mockBaseRule(productCatalog, expresionList, mock(RepositoryItem.class), "ruleCategory", true);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.US);

        assertEquals("(ancestorCategoryId:ruleCategory) OR (salePriceUS:[25 TO 100])", builder);
    }

    @Test
    public void testBuildSimpleKeywordRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("keyword", 1, "key", null));
        mockBaseRule(productCatalog, expresionList, mock(RepositoryItem.class), "ruleCategory", true);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.US);

        assertEquals("(ancestorCategoryId:ruleCategory) OR (keyword:key)", builder);
    }

    @Test
    public void testBuildComplexKeywordRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        String keyword = "this is a big weird keyword'";
        expresionList.add(mockRule("keyword", 1, keyword, null));
        mockBaseRule(productCatalog, expresionList, mock(RepositoryItem.class), "ruleCategory", true);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.US);

        assertEquals("(ancestorCategoryId:ruleCategory) OR (keyword:" + ClientUtils.escapeQueryChars(keyword) + ")", builder);
    }

    @Test
    public void testBuildComplexRule() throws RepositoryException {
        // Items from brand 88 that are part of cat1 and cat2 and are not on
        // sale
        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("brand", 1, "88", null));
        expresionList.add(mockRule("category", 2, "cat1", "AND"));
        expresionList.add(mockRule("category", 2, "cat2", "AND"));
        expresionList.add(mockRule("show_sale", 3, "false", "AND"));
        mockBaseRule(productCatalog, expresionList, mock(RepositoryItem.class), "ruleCategory", true);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.US);

        assertEquals("(ancestorCategoryId:ruleCategory) OR (brandId:88 AND (ancestorCategoryId:cat1 AND ancestorCategoryId:cat2 AND (onsaleUS:false)))", builder);
    }

    @Test
    public void testBuildComplexRule2() throws RepositoryException {
        // Items from brand 88 that are part of cat1 and cat2 or
        // items from brand 77 that are on sale
        // and none should be past season
        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("brand", 1, "88", null));
        expresionList.add(mockRule("category", 1, "cat1", "AND"));
        expresionList.add(mockRule("category", 1, "cat2", "AND"));
        expresionList.add(mockRule("brand", 2, "77", "OR"));
        expresionList.add(mockRule("show_sale", 2, "true", "AND"));
        expresionList.add(mockRule("past_season", 4, "false", "AND"));
        mockBaseRule(productCatalog, expresionList, mock(RepositoryItem.class), "ruleCategory", true);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.US);

        assertEquals(
                "(ancestorCategoryId:ruleCategory) OR (brandId:88 AND ancestorCategoryId:cat1 AND ancestorCategoryId:cat2 OR (brandId:77 AND onsaleUS:true AND (isPastSeason:false)))",
                builder);
    }

    @Test
    public void testBuildComplexRule3() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("brand", 1, "88", null));
        expresionList.add(mockRule("category", 1, "cat1", "AND"));
        expresionList.add(mockRule("category", 2, "cat2", "AND"));
        expresionList.add(mockRule("brand", 3, "77", "OR"));
        expresionList.add(mockRule("brand", 3, "88", "OR"));
        expresionList.add(mockRule("show_sale", 2, "true", "AND"));
        expresionList.add(mockRule("past_season", 1, "false", "AND"));
        mockBaseRule(productCatalog, expresionList, mock(RepositoryItem.class), "ruleCategory", true);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.US);

        assertEquals(
                "(ancestorCategoryId:ruleCategory) OR (brandId:88 AND ancestorCategoryId:cat1 AND (ancestorCategoryId:cat2 OR (brandId:77 OR brandId:88) AND onsaleUS:true) AND isPastSeason:false)",
                builder);
    }
    
     @Test
     public void testChildCategories() throws RepositoryException{
         RepositoryItem ruleCategory = mock(RepositoryItem.class);
         RepositoryItem child1Category = mock(RepositoryItem.class);
         RepositoryItem child2Category = mock(RepositoryItem.class);
                 
         List<RepositoryItem> childList = new ArrayList<RepositoryItem>();
         childList.add(child1Category);
         childList.add(child2Category);
         when(ruleCategory.getPropertyValue(CategoryProperty.FIXED_CHILD_CATEGORIES)).thenReturn(childList);
         
         List<RepositoryItem> rootExpresionList = new ArrayList<RepositoryItem>();        
         mockBaseRule(productCatalog, rootExpresionList, ruleCategory, "ruleCategory", true);
         
         List<RepositoryItem> cat1ExpresionList = new ArrayList<RepositoryItem>();
         cat1ExpresionList.add(mockRule("pct_off", 1, "15", null));
         mockBaseRule(productCatalog, cat1ExpresionList, child1Category, "childCat1", false);
         
         List<RepositoryItem> cat2ExpresionList = new ArrayList<RepositoryItem>();
         cat2ExpresionList.add(mockRule("brand", 1, "88", null));
         mockBaseRule(productCatalog, cat2ExpresionList, child2Category, "childCat2", false);
         
         String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.US);
         
         assertEquals("(ancestorCategoryId:ruleCategory) OR ((ancestorCategoryId:childCat1) OR (discountPercentUS:[15 TO 100])) OR ((ancestorCategoryId:childCat2) OR (brandId:88))", builder);
     }    

}

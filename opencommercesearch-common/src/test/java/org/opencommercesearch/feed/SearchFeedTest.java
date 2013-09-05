package org.opencommercesearch.feed;

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

import atg.commerce.inventory.InventoryException;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryItemDescriptor;

import org.apache.solr.client.solrj.response.UpdateResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.opencommercesearch.SearchServer;
import org.opencommercesearch.model.Product;
import org.opencommercesearch.model.Sku;
import org.opencommercesearch.repository.RuleBasedCategoryProperty;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.*;

//@RunWith(MockitoJUnitRunner.class)
public class SearchFeedTest {

    private SearchFeed feed = new SearchFeed() {
        protected void cleanupDocuments(SearchServer searchServer, List<String> documentsToDelete) {
            throw new UnsupportedOperationException();
        }

        protected void onFeedStarted(long indexStamp) {
            throw new UnsupportedOperationException();
        }

        protected void onFeedFinished(long indexStamp) {
            throw new UnsupportedOperationException();
        }

        protected void onProductsSent(UpdateResponse response, List<Product> productList) {
            throw new UnsupportedOperationException();
        }

        protected void onProductsSentError(List<Product> productList) {
            throw new UnsupportedOperationException();
        }

        protected void processProduct(RepositoryItem productItem, Map<Locale, List<Product>> products)
                throws RepositoryException, InventoryException {
            throw new UnsupportedOperationException();
        }

    };

    @Mock
    private RepositoryItem catalogOutdoor;
    @Mock
    private RepositoryItem catRoot;
    @Mock
    private RepositoryItem catRulesBased;
    @Mock
    private RepositoryItem catShoesFootwear;
    @Mock
    private RepositoryItem catMensShoesBoots;
    @Mock
    private RepositoryItem catMensRainBootsShoes;
    @Mock
    private RepositoryItem catMensRainShoes;
    @Mock
    private RepositoryItem catMensRainBoots;
    @Mock
    private RepositoryItem catMensClothing;
    @Mock
    private RepositoryItem catMensShoesFootwear;
    @Mock
    private RepositoryItem catSnowshoe;
    @Mock
    private RepositoryItem catSnowshoeAccessories;
    @Mock
    private RepositoryItem catSnowshoeFootwear;
    @Mock
    private RepositoryItem catSnowshoeBoots;
    @Mock
    private RepositoryItem prodMensBoot;
    @Mock
    private Sku sku;
    @Captor 
    private ArgumentCaptor<String> stringCaptor;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        /**
         * root
         *     Shoes & Footwear
         *         Men's Shoes & Boots
         *             Men's Rain Boots & Shoes
         *                 Men's Rain Shoes
         *                 Men's Rain Boots
         *     Men's Clothing
         *         Men's Shoes & Footwear
         *             Mens Rain Boots & Shoes
         *                 Men's Rain Shoes
         *                 Men's Rain Boots
         *    Snowshoe
         *         Snowshoe Accessories
         *             Snowshoe Footwear
         *                 Snowshoe boots
         */

        // document
        when(sku.getCategoryTokens()).thenReturn(new HashSet<String>());

        // catalogs
        when(catalogOutdoor.getRepositoryId()).thenReturn("outdoorCatalog");
        Set<RepositoryItem> categoryCatalogs = newSet(catalogOutdoor);

        // Root
        mockCategory(catRoot, "catRoot", "root", categoryCatalogs, null, "category");
        // Rules Based
        mockCategory(catRulesBased, "catRulesBased", "Rules Based", categoryCatalogs, newSet(catRoot), RuleBasedCategoryProperty.ITEM_DESCRIPTOR);
        // Shoes & Footwear
        mockCategory(catShoesFootwear, "outdoorCat4000003", "Shoes & Footwear", categoryCatalogs, newSet(catRoot), "category");
        // Men's Shoes & Boots
        mockCategory(catMensShoesBoots, "outdoorCat4100004", "Men's Shoes & Boots", categoryCatalogs, newSet(catShoesFootwear), "category");
        // Men's Clothing
        mockCategory(catMensClothing, "outdoorCat100003", "Men's Clothing", categoryCatalogs, newSet(catRoot), "category");
        // Men's Shoes & Footwear
        mockCategory(catMensShoesFootwear, "outdoorCat11000219", "Men's Shoes & Footwear", categoryCatalogs, newSet(catMensClothing), "category");
        // Men's Rain Boots & Shoes
        mockCategory(catMensRainBootsShoes, "outdoorCat41100024", "Men's Rain Boots & Shoes", categoryCatalogs, newSet(catMensShoesBoots, catMensShoesFootwear), "category");
        // Men's Rain Shoes
        mockCategory(catMensRainShoes, "outdoorCat41110026", "Men's Rain Shoes", categoryCatalogs, newSet(catMensRainBootsShoes), "category");
        // Men's Rain Boots
        mockCategory(catMensRainBoots, "outdoorCat41110025", "Men's Rain Boots", categoryCatalogs, newSet(catMensRainBootsShoes), "category");
        // Snowshoe
        mockCategory(catSnowshoe, "outdoorCat11000003", "Snowshoe", categoryCatalogs, newSet(catRoot), "category");
        // Snowshoe Accessories
        mockCategory(catSnowshoeAccessories, "outdoorCat111000028", "Snowshoe Accessories", categoryCatalogs, newSet(catSnowshoe), "category");
        // Snowshoe Footwear
        mockCategory(catSnowshoeFootwear, "outdoorCat111100030", "Snowshoe Footwear", categoryCatalogs, newSet(catSnowshoeAccessories), "category");
        // Snowshoe boots
        mockCategory(catSnowshoeBoots, "outdoorCat111110031", "Snowshoe Boots", categoryCatalogs, newSet(catSnowshoeFootwear), "category");
        
        
        when(prodMensBoot.getPropertyValue("parentCategories")).thenReturn(newSet(catMensRainShoes, catMensRainBoots, catSnowshoeBoots));

        // feed
        feed.setLoggingInfo(false);
        feed.setLoggingDebug(false);
        feed.setLoggingWarning(false);
        feed.setLoggingTrace(false);
        feed.setLoggingError(false);
    }

    @Test
    public void testDuplicateCategories() throws RepositoryException, InventoryException {
        Set<RepositoryItem> catalogAssignments = null;
        Set<RepositoryItem> categoryCatalogs = null;

        feed.loadCategoryPaths(sku, prodMensBoot, newSet(catalogOutdoor), newSet(catalogOutdoor));

        verify(sku, times(1)).addCategoryToken("0.outdoorCatalog");
        verify(sku, times(1)).addCategoryToken("1.outdoorCatalog.Shoes & Footwear");
        verify(sku, times(1)).addCategoryToken("2.outdoorCatalog.Shoes & Footwear.Men's Shoes & Boots");
        verify(sku, times(1)).addCategoryToken("3.outdoorCatalog.Shoes & Footwear.Men's Shoes & Boots.Men's Rain Boots & Shoes");
        verify(sku, times(1)).addCategoryToken("4.outdoorCatalog.Shoes & Footwear.Men's Shoes & Boots.Men's Rain Boots & Shoes.Men's Rain Shoes");
        verify(sku, times(1)).addCategoryToken("4.outdoorCatalog.Shoes & Footwear.Men's Shoes & Boots.Men's Rain Boots & Shoes.Men's Rain Boots");
        verify(sku, times(1)).addCategoryToken("1.outdoorCatalog.Men's Clothing");
        verify(sku, times(1)).addCategoryToken("2.outdoorCatalog.Men's Clothing.Men's Shoes & Footwear");
        verify(sku, times(1)).addCategoryToken("3.outdoorCatalog.Men's Clothing.Men's Shoes & Footwear.Men's Rain Boots & Shoes");
        verify(sku, times(1)).addCategoryToken("4.outdoorCatalog.Men's Clothing.Men's Shoes & Footwear.Men's Rain Boots & Shoes.Men's Rain Shoes");
        verify(sku, times(1)).addCategoryToken("4.outdoorCatalog.Men's Clothing.Men's Shoes & Footwear.Men's Rain Boots & Shoes.Men's Rain Boots");
        verify(sku, times(1)).addCategoryToken("1.outdoorCatalog.Snowshoe");
        verify(sku, times(1)).addCategoryToken("2.outdoorCatalog.Snowshoe.Snowshoe Accessories");
        verify(sku, times(1)).addCategoryToken("3.outdoorCatalog.Snowshoe.Snowshoe Accessories.Snowshoe Footwear");
        verify(sku, times(1)).addCategoryToken("4.outdoorCatalog.Snowshoe.Snowshoe Accessories.Snowshoe Footwear.Snowshoe Boots");
        
        verify(sku, times(1)).addCategoryPath("outdoorCatalog");
        verify(sku, times(1)).addCategoryPath("outdoorCatalog.outdoorCat4000003");
        verify(sku, times(1)).addCategoryPath("outdoorCatalog.outdoorCat4000003.outdoorCat4100004");
        verify(sku, times(1)).addCategoryPath("outdoorCatalog.outdoorCat4000003.outdoorCat4100004.outdoorCat41100024");
        verify(sku, times(1)).addCategoryPath("outdoorCatalog.outdoorCat4000003.outdoorCat4100004.outdoorCat41100024.outdoorCat41110026");
        verify(sku, times(1)).addCategoryPath("outdoorCatalog.outdoorCat4000003.outdoorCat4100004.outdoorCat41100024.outdoorCat41110025");
        verify(sku, times(1)).addCategoryPath("outdoorCatalog.outdoorCat100003");
        verify(sku, times(1)).addCategoryPath("outdoorCatalog.outdoorCat100003.outdoorCat11000219");
        verify(sku, times(1)).addCategoryPath("outdoorCatalog.outdoorCat100003.outdoorCat11000219.outdoorCat41100024");
        verify(sku, times(1)).addCategoryPath("outdoorCatalog.outdoorCat100003.outdoorCat11000219.outdoorCat41100024.outdoorCat41110026");
        verify(sku, times(1)).addCategoryPath("outdoorCatalog.outdoorCat100003.outdoorCat11000219.outdoorCat41100024.outdoorCat41110025");
        verify(sku, times(1)).addCategoryPath("outdoorCatalog.outdoorCat11000003");
        verify(sku, times(1)).addCategoryPath("outdoorCatalog.outdoorCat11000003.outdoorCat111000028");
        verify(sku, times(1)).addCategoryPath("outdoorCatalog.outdoorCat11000003.outdoorCat111000028.outdoorCat111100030");
        verify(sku, times(1)).addCategoryPath("outdoorCatalog.outdoorCat11000003.outdoorCat111000028.outdoorCat111100030.outdoorCat111110031");
        //verify(sku, times(1)).addCategoryToken("4.outdoorCatalog.Snowshoe.Snowshoe Accessories.Snowshoe Footwear.Men's Rain Boots");

        verify(sku, times(15)).addCategoryToken(anyString());

        // verify leaf category ids
        verify(sku, times(1)).addAncestorCategory("outdoorCat4000003");
        verify(sku, times(1)).addAncestorCategory("outdoorCat4100004");
        verify(sku, times(1)).addAncestorCategory("outdoorCat41100024");
        verify(sku, times(1)).addAncestorCategory("outdoorCat41110026");
        verify(sku, times(1)).addAncestorCategory("outdoorCat41110025");
        verify(sku, times(1)).addAncestorCategory("outdoorCat11000219");
        verify(sku, times(1)).addAncestorCategory("outdoorCat111000028");
        verify(sku, times(1)).addAncestorCategory("outdoorCat111100030");
        verify(sku, times(1)).addAncestorCategory("outdoorCat111110031");
        verify(sku, times(8)).addCategoryNode(stringCaptor.capture());
        
        assertEquals("Men's Clothing", stringCaptor.getAllValues().get(0));
        assertEquals("Men's Shoes & Boots", stringCaptor.getAllValues().get(1));
        assertEquals("Snowshoe Footwear", stringCaptor.getAllValues().get(2));
        assertEquals("Snowshoe Accessories", stringCaptor.getAllValues().get(3));
        assertEquals("Snowshoe", stringCaptor.getAllValues().get(4));
        assertEquals("Shoes & Footwear", stringCaptor.getAllValues().get(5));
        assertEquals("Men's Shoes & Footwear", stringCaptor.getAllValues().get(6));
        assertEquals("Men's Rain Boots & Shoes", stringCaptor.getAllValues().get(7));
        

        stringCaptor = ArgumentCaptor.forClass(String.class);
        verify(sku, times(3)).addCategoryLeaf(stringCaptor.capture());
        assertEquals("Men's Rain Shoes", stringCaptor.getAllValues().get(0));
        assertEquals("Snowshoe Boots", stringCaptor.getAllValues().get(1));
        assertEquals("Men's Rain Boots", stringCaptor.getAllValues().get(2));
    }

    
    @Test
    public void testCategoryNotInCurrentCatalog() throws RepositoryException, InventoryException {
    
    	RepositoryItem otherCatalog = mock(RepositoryItem.class);   
    	when(otherCatalog.getRepositoryId()).thenReturn("otherCatalog");
    	Set<RepositoryItem> categoryCatalogs = newSet(otherCatalog);
    	
    	RepositoryItem rootOtherCategory = mock(RepositoryItem.class);    	
    	mockCategory(rootOtherCategory, "rootOtherCategory", "Root Other Category", categoryCatalogs, null, "category");
    	
    	RepositoryItem otherCategory = mock(RepositoryItem.class);    	
    	mockCategory(otherCategory, "otherCategory", "Other Category", categoryCatalogs, newSet(rootOtherCategory), "category");
    	
    	RepositoryItem product = mock(RepositoryItem.class);    	
    	when(product.getPropertyValue("parentCategories")).thenReturn(newSet(catSnowshoeBoots, otherCategory));
    	 
        feed.loadCategoryPaths(sku, product, newSet(catalogOutdoor), newSet(catalogOutdoor));
     
        verify(sku, times(1)).addCategoryToken("0.outdoorCatalog");
        verify(sku, times(1)).addCategoryToken("1.outdoorCatalog.Snowshoe");
        verify(sku, times(1)).addCategoryToken("2.outdoorCatalog.Snowshoe.Snowshoe Accessories");
        verify(sku, times(1)).addCategoryToken("3.outdoorCatalog.Snowshoe.Snowshoe Accessories.Snowshoe Footwear");
        verify(sku, times(1)).addCategoryToken("4.outdoorCatalog.Snowshoe.Snowshoe Accessories.Snowshoe Footwear.Snowshoe Boots");
        verify(sku, never()) .addCategoryToken("0.otherCatalog");
        verify(sku, never()) .addCategoryToken("1.otherCatalog.Other Category");
        verify(sku, times(5)).addCategoryToken(anyString());
        
        verify(sku, times(1)).addCategoryPath("outdoorCatalog");
        verify(sku, times(1)).addCategoryPath("outdoorCatalog.outdoorCat11000003");
        verify(sku, times(1)).addCategoryPath("outdoorCatalog.outdoorCat11000003.outdoorCat111000028");
        verify(sku, times(1)).addCategoryPath("outdoorCatalog.outdoorCat11000003.outdoorCat111000028.outdoorCat111100030");
        verify(sku, times(1)).addCategoryPath("outdoorCatalog.outdoorCat11000003.outdoorCat111000028.outdoorCat111100030.outdoorCat111110031");
        verify(sku, times(5)).addCategoryPath(anyString());

        verify(sku, times(1)).addAncestorCategory("outdoorCat11000003");
        verify(sku, times(1)).addAncestorCategory("outdoorCat111000028");
        verify(sku, times(1)).addAncestorCategory("outdoorCat111100030");
        verify(sku, times(1)).addAncestorCategory("outdoorCat111110031");
        verify(sku, never()).addAncestorCategory("otherCategory");
        verify(sku, atMost(5)).addAncestorCategory(anyString());
        
    }
    
    @Test
    public void testRulesBasedCategory() throws RepositoryException, InventoryException {
    
    	RepositoryItem product = mock(RepositoryItem.class);
    	
    	when(product.getPropertyValue("parentCategories")).thenReturn(newSet(catSnowshoeBoots, catRulesBased));
    	 
        feed.loadCategoryPaths(sku, product, newSet(catalogOutdoor), newSet(catalogOutdoor));
     
        verify(sku, times(1)).addCategoryToken("0.outdoorCatalog");
        verify(sku, times(1)).addCategoryToken("1.outdoorCatalog.Snowshoe");
        verify(sku, times(1)).addCategoryToken("2.outdoorCatalog.Snowshoe.Snowshoe Accessories");
        verify(sku, times(1)).addCategoryToken("3.outdoorCatalog.Snowshoe.Snowshoe Accessories.Snowshoe Footwear");
        verify(sku, times(1)).addCategoryToken("4.outdoorCatalog.Snowshoe.Snowshoe Accessories.Snowshoe Footwear.Snowshoe Boots");
        verify(sku, times(5)).addCategoryToken(anyString());
        
        verify(sku, times(1)).addCategoryPath("outdoorCatalog");
        verify(sku, times(1)).addCategoryPath("outdoorCatalog.outdoorCat11000003");
        verify(sku, times(1)).addCategoryPath("outdoorCatalog.outdoorCat11000003.outdoorCat111000028");
        verify(sku, times(1)).addCategoryPath("outdoorCatalog.outdoorCat11000003.outdoorCat111000028.outdoorCat111100030");
        verify(sku, times(1)).addCategoryPath("outdoorCatalog.outdoorCat11000003.outdoorCat111000028.outdoorCat111100030.outdoorCat111110031");
        verify(sku, times(5)).addCategoryPath(anyString());

        verify(sku, times(1)).addAncestorCategory("outdoorCat11000003");
        verify(sku, times(1)).addAncestorCategory("outdoorCat111000028");
        verify(sku, times(1)).addAncestorCategory("outdoorCat111100030");
        verify(sku, times(1)).addAncestorCategory("outdoorCat111110031");

        // for rule based categories we only index the ancestor id. This is to support hand pick rules.
        verify(sku, never()).addCategoryToken("1.outdoorCatalog.Rules Based");
        verify(sku, never()).addCategoryPath("outdoorCatalog.catRulesBased");
        verify(sku, never()).addCategoryLeaf("Rules Based");
        verify(sku, times(1)).addAncestorCategory("catRulesBased");

    }
        
    private void mockCategory(RepositoryItem category, String categoryId, String displayName, Set<RepositoryItem> categoryCatalogs, Set<RepositoryItem>  parentCategories, String itemDescriptorName) throws RepositoryException{
    	when(category.getRepositoryId()).thenReturn(categoryId);
        when(category.getItemDisplayName()).thenReturn(displayName);
        when(category.getPropertyValue("catalogs")).thenReturn(categoryCatalogs);
        when(category.getPropertyValue("fixedParentCategories")).thenReturn(parentCategories);
        RepositoryItemDescriptor itemDescriptor = mock(RepositoryItemDescriptor.class);
        when(itemDescriptor.getItemDescriptorName()).thenReturn(itemDescriptorName);
		when(category.getItemDescriptor()).thenReturn(itemDescriptor );
    }
    
    private Set<RepositoryItem> newSet(RepositoryItem... items) {
        Set<RepositoryItem> set = new HashSet<RepositoryItem>(items.length);
        for (RepositoryItem item : items) {
            set.add(item);
        }
        return set;
    }
}

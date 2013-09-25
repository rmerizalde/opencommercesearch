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

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.opencommercesearch.SearchServer;
import org.opencommercesearch.model.Product;
import org.opencommercesearch.model.Sku;
import org.opencommercesearch.repository.RuleBasedCategoryProperty;
import org.restlet.Response;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
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

        protected void onProductsSent(Response response, List<Product> productList) {
            throw new UnsupportedOperationException();
        }

        protected void onProductsSentError(List<Product> productList) {
            throw new UnsupportedOperationException();
        }

        protected void processProduct(RepositoryItem productItem, SearchFeedProducts products)
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
        final Set<String> categories = new HashSet<String>();

        doCallRealMethod().when(sku).setAssigned(anyBoolean());
        when(sku.isAssigned()).thenCallRealMethod();

        feed.checkSkuAssigned(sku, prodMensBoot, newSet(catalogOutdoor));

        /*verify(sku, times(1)).addCategory("catRoot");
        verify(sku, times(1)).addCategory("outdoorCat4000003");
        verify(sku, times(1)).addCategory("outdoorCat4100004");
        verify(sku, times(1)).addCategory("outdoorCat41100024");
        verify(sku, times(1)).addCategory("outdoorCat41110026");
        verify(sku, times(1)).addCategory("outdoorCat41110025");
        verify(sku, times(1)).addCategory("outdoorCat100003");
        verify(sku, times(1)).addCategory("outdoorCat11000219");
        verify(sku, times(1)).addCategory("outdoorCat11000003");
        verify(sku, times(1)).addCategory("outdoorCat111000028");
        verify(sku, times(1)).addCategory("outdoorCat111100030");
        verify(sku, times(1)).addCategory("outdoorCat111110031");
        verify(sku, never()).addCategory(anyString()); */
        verify(sku).setAssigned(true);
    }


    @Test
    public void testCategoryNotInCurrentCatalog() throws RepositoryException, InventoryException {

        // @todo: seems like this test isn't useful anymore
        doCallRealMethod().when(sku).setAssigned(anyBoolean());
        when(sku.isAssigned()).thenCallRealMethod();

    	RepositoryItem otherCatalog = mock(RepositoryItem.class);
    	when(otherCatalog.getRepositoryId()).thenReturn("otherCatalog");
    	Set<RepositoryItem> categoryCatalogs = newSet(otherCatalog);

    	RepositoryItem rootOtherCategory = mock(RepositoryItem.class);
    	mockCategory(rootOtherCategory, "rootOtherCategory", "Root Other Category", categoryCatalogs, null, "category");

    	RepositoryItem otherCategory = mock(RepositoryItem.class);
    	mockCategory(otherCategory, "otherCategory", "Other Category", categoryCatalogs, newSet(rootOtherCategory), "category");

    	RepositoryItem product = mock(RepositoryItem.class);
    	when(product.getPropertyValue("parentCategories")).thenReturn(newSet(catSnowshoeBoots, otherCategory));

        feed.checkSkuAssigned(sku, product, newSet(catalogOutdoor));

        /*verify(sku, times(1)).addCategory("catRoot");
        verify(sku, times(1)).addCategory("outdoorCat11000003");
        verify(sku, times(1)).addCategory("outdoorCat111000028");
        verify(sku, times(1)).addCategory("outdoorCat111100030");
        verify(sku, times(1)).addCategory("outdoorCat111110031");
        verify(sku, times(5)).addCategory(anyString()); */
        verify(sku).setAssigned(true);
    }

    @Test
    public void testRulesBasedCategory() throws RepositoryException, InventoryException {

        doCallRealMethod().when(sku).setAssigned(anyBoolean());
        when(sku.isAssigned()).thenCallRealMethod();

    	RepositoryItem product = mock(RepositoryItem.class);

    	when(product.getPropertyValue("parentCategories")).thenReturn(newSet(catSnowshoeBoots, catRulesBased));

        feed.checkSkuAssigned(sku, product, newSet(catalogOutdoor));

        /*verify(sku, times(1)).addCategory("catRoot");
        verify(sku, times(1)).addCategory("outdoorCat11000003");
        verify(sku, times(1)).addCategory("outdoorCat111000028");
        verify(sku, times(1)).addCategory("outdoorCat111100030");
        verify(sku, times(1)).addCategory("outdoorCat111110031");
        verify(sku, times(1)).addCategory("catRulesBased");
        verify(sku, times(6)).addCategory(anyString());*/
        verify(sku).setAssigned(true);
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

package org.opencommercesearch.feed;

import atg.commerce.inventory.InventoryException;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryItemDescriptor;

import org.apache.solr.common.SolrInputDocument;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opencommercesearch.SearchServer;
import org.opencommercesearch.repository.RuleBasedCategoryProperty;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.*;

@RunWith(MockitoJUnitRunner.class)
public class SearchFeedTest {
    private SearchFeed feed = new SearchFeed() {
        protected void cleanupDocuments(SearchServer searchServer, List<String> documentsToDelete) {
            throw new UnsupportedOperationException();
        }

        protected void feedStarted() {
            throw new UnsupportedOperationException();
        }

        protected void feedFinished() {
            throw new UnsupportedOperationException();
        }

        protected void processProduct(RepositoryItem product, Map<Locale, List<SolrInputDocument>> documents)
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
    private SolrInputDocument solrDocument;

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
        when(solrDocument.getFieldValues("category")).thenReturn(new HashSet<Object>());

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

        feed.loadCategoryPaths(solrDocument, prodMensBoot, newSet(catalogOutdoor), newSet(catalogOutdoor));

        verify(solrDocument, times(1)).addField("category", "0.outdoorCatalog");
        verify(solrDocument, times(1)).addField("category", "1.outdoorCatalog.Shoes & Footwear");
        verify(solrDocument, times(1)).addField("category", "2.outdoorCatalog.Shoes & Footwear.Men's Shoes & Boots");
        verify(solrDocument, times(1)).addField("category", "3.outdoorCatalog.Shoes & Footwear.Men's Shoes & Boots.Men's Rain Boots & Shoes");
        verify(solrDocument, times(1)).addField("category", "4.outdoorCatalog.Shoes & Footwear.Men's Shoes & Boots.Men's Rain Boots & Shoes.Men's Rain Shoes");
        verify(solrDocument, times(1)).addField("category", "4.outdoorCatalog.Shoes & Footwear.Men's Shoes & Boots.Men's Rain Boots & Shoes.Men's Rain Boots");
        verify(solrDocument, times(1)).addField("category", "1.outdoorCatalog.Men's Clothing");
        verify(solrDocument, times(1)).addField("category", "2.outdoorCatalog.Men's Clothing.Men's Shoes & Footwear");
        verify(solrDocument, times(1)).addField("category", "3.outdoorCatalog.Men's Clothing.Men's Shoes & Footwear.Men's Rain Boots & Shoes");
        verify(solrDocument, times(1)).addField("category", "4.outdoorCatalog.Men's Clothing.Men's Shoes & Footwear.Men's Rain Boots & Shoes.Men's Rain Shoes");
        verify(solrDocument, times(1)).addField("category", "4.outdoorCatalog.Men's Clothing.Men's Shoes & Footwear.Men's Rain Boots & Shoes.Men's Rain Boots");
        verify(solrDocument, times(1)).addField("category", "1.outdoorCatalog.Snowshoe");
        verify(solrDocument, times(1)).addField("category", "2.outdoorCatalog.Snowshoe.Snowshoe Accessories");
        verify(solrDocument, times(1)).addField("category", "3.outdoorCatalog.Snowshoe.Snowshoe Accessories.Snowshoe Footwear");
        verify(solrDocument, times(1)).addField("category", "4.outdoorCatalog.Snowshoe.Snowshoe Accessories.Snowshoe Footwear.Snowshoe Boots");
        
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog");
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog.outdoorCat4000003");
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog.outdoorCat4000003.outdoorCat4100004");
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog.outdoorCat4000003.outdoorCat4100004.outdoorCat41100024");
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog.outdoorCat4000003.outdoorCat4100004.outdoorCat41100024.outdoorCat41110026");
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog.outdoorCat4000003.outdoorCat4100004.outdoorCat41100024.outdoorCat41110025");
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog.outdoorCat100003");
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog.outdoorCat100003.outdoorCat11000219");
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog.outdoorCat100003.outdoorCat11000219.outdoorCat41100024");
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog.outdoorCat100003.outdoorCat11000219.outdoorCat41100024.outdoorCat41110026");
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog.outdoorCat100003.outdoorCat11000219.outdoorCat41100024.outdoorCat41110025");
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog.outdoorCat11000003");
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog.outdoorCat11000003.outdoorCat111000028");
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog.outdoorCat11000003.outdoorCat111000028.outdoorCat111100030");
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog.outdoorCat11000003.outdoorCat111000028.outdoorCat111100030.outdoorCat111110031");
        //verify(solrDocument, times(1)).addField("category", "4.outdoorCatalog.Snowshoe.Snowshoe Accessories.Snowshoe Footwear.Men's Rain Boots");

        verify(solrDocument, times(15)).addField(eq("category"), anyString());

        // verify leaf category ids
        verify(solrDocument, times(1)).addField("categoryId", "outdoorCat41110026");
        verify(solrDocument, times(1)).addField("categoryId", "outdoorCat41110025");
        verify(solrDocument, times(1)).addField("categoryId", "outdoorCat111110031");
        verify(solrDocument, times(3)).addField(eq("categoryId"), anyString());
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
    	 
        feed.loadCategoryPaths(solrDocument, product, newSet(catalogOutdoor), newSet(catalogOutdoor));
     
        verify(solrDocument, times(1)).addField("category", "0.outdoorCatalog");
        verify(solrDocument, times(1)).addField("category", "1.outdoorCatalog.Snowshoe");
        verify(solrDocument, times(1)).addField("category", "2.outdoorCatalog.Snowshoe.Snowshoe Accessories");
        verify(solrDocument, times(1)).addField("category", "3.outdoorCatalog.Snowshoe.Snowshoe Accessories.Snowshoe Footwear");
        verify(solrDocument, times(1)).addField("category", "4.outdoorCatalog.Snowshoe.Snowshoe Accessories.Snowshoe Footwear.Snowshoe Boots");
        verify(solrDocument, never()) .addField("category", "0.otherCatalog");
        verify(solrDocument, never()) .addField("category", "1.otherCatalog.Other Category");
        verify(solrDocument, times(5)).addField(eq("category"), anyString());
        
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog");
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog.outdoorCat11000003");
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog.outdoorCat11000003.outdoorCat111000028");
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog.outdoorCat11000003.outdoorCat111000028.outdoorCat111100030");
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog.outdoorCat11000003.outdoorCat111000028.outdoorCat111100030.outdoorCat111110031");
        verify(solrDocument, never()) .addField("category", "otherCatalog");
        verify(solrDocument, never()) .addField("category", "otherCatalog.otherCategory");
        verify(solrDocument, times(5)).addField(eq("categoryPath"), anyString());
        
        verify(solrDocument, times(1)).addField("categoryId", "outdoorCat111110031");
        verify(solrDocument, never() ).addField("categoryId", "otherCategory");
        verify(solrDocument, times(1)).addField(eq("categoryId"), anyString());
        
    }
    
    @Test
    public void testRulesBasedCategory() throws RepositoryException, InventoryException {
    
    	RepositoryItem product = mock(RepositoryItem.class);
    	
    	when(product.getPropertyValue("parentCategories")).thenReturn(newSet(catSnowshoeBoots, catRulesBased));
    	 
        feed.loadCategoryPaths(solrDocument, product, newSet(catalogOutdoor), newSet(catalogOutdoor));
     
        verify(solrDocument, times(1)).addField("category", "0.outdoorCatalog");
        verify(solrDocument, times(1)).addField("category", "1.outdoorCatalog.Snowshoe");
        verify(solrDocument, times(1)).addField("category", "2.outdoorCatalog.Snowshoe.Snowshoe Accessories");
        verify(solrDocument, times(1)).addField("category", "3.outdoorCatalog.Snowshoe.Snowshoe Accessories.Snowshoe Footwear");
        verify(solrDocument, times(1)).addField("category", "4.outdoorCatalog.Snowshoe.Snowshoe Accessories.Snowshoe Footwear.Snowshoe Boots");
        verify(solrDocument, never()) .addField("category", "1.outdoorCatalog.Rules Based");
        verify(solrDocument, times(5)).addField(eq("category"), anyString());
        
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog");
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog.outdoorCat11000003");
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog.outdoorCat11000003.outdoorCat111000028");
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog.outdoorCat11000003.outdoorCat111000028.outdoorCat111100030");
        verify(solrDocument, times(1)).addField("categoryPath", "outdoorCatalog.outdoorCat11000003.outdoorCat111000028.outdoorCat111100030.outdoorCat111110031");
        verify(solrDocument, never()) .addField("categoryPath", "outdoorCatalog.catRulesBased");
        verify(solrDocument, times(5)).addField(eq("categoryPath"), anyString());
        
        verify(solrDocument, times(1)).addField("categoryId", "outdoorCat111110031");
        verify(solrDocument, times(1)).addField("categoryId", "catRulesBased");
        verify(solrDocument, times(2)).addField(eq("categoryId"), anyString());
        
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

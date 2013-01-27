package org.opencommercesearch.feed;

import atg.commerce.inventory.InventoryException;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import org.apache.solr.common.SolrInputDocument;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opencommercesearch.SearchServer;

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
        when(catRoot.getRepositoryId()).thenReturn("catRoot");
        when(catRoot.getItemDisplayName()).thenReturn("root");
        when(catRoot.getPropertyValue("catalogs")).thenReturn(categoryCatalogs);
        //when(catRoot.getPropertyValue("fixedParentCategories")).thenReturn(newSet(null));

        // Shoes & Footwear
        when(catShoesFootwear.getRepositoryId()).thenReturn("outdoorCat4000003");
        when(catShoesFootwear.getItemDisplayName()).thenReturn("Shoes & Footwear");
        when(catShoesFootwear.getPropertyValue("catalogs")).thenReturn(categoryCatalogs);
        when(catShoesFootwear.getPropertyValue("fixedParentCategories")).thenReturn(newSet(catRoot));

        // Men's Shoes & Boots
        when(catMensShoesBoots.getRepositoryId()).thenReturn("outdoorCat4100004");
        when(catMensShoesBoots.getItemDisplayName()).thenReturn("Men's Shoes & Boots");
        when(catMensShoesBoots.getPropertyValue("catalogs")).thenReturn(categoryCatalogs);
        when(catMensShoesBoots.getPropertyValue("fixedParentCategories")).thenReturn(newSet(catShoesFootwear));

        // Men's Clothing
        when(catMensClothing.getRepositoryId()).thenReturn("outdoorCat100003");
        when(catMensClothing.getItemDisplayName()).thenReturn("Men's Clothing");
        when(catMensClothing.getPropertyValue("catalogs")).thenReturn(categoryCatalogs);
        when(catMensClothing.getPropertyValue("fixedParentCategories")).thenReturn(newSet(catRoot));

        // Men's Shoes & Footwear
        when(catMensShoesFootwear.getRepositoryId()).thenReturn("outdoorCat11000219");
        when(catMensShoesFootwear.getItemDisplayName()).thenReturn("Men's Shoes & Footwear");
        when(catMensShoesFootwear.getPropertyValue("catalogs")).thenReturn(categoryCatalogs);
        when(catMensShoesFootwear.getPropertyValue("fixedParentCategories")).thenReturn(newSet(catMensClothing));

        // Men's Rain Boots & Shoes
        when(catMensRainBootsShoes.getRepositoryId()).thenReturn("outdoorCat41100024");
        when(catMensRainBootsShoes.getItemDisplayName()).thenReturn("Men's Rain Boots & Shoes");
        when(catMensRainBootsShoes.getPropertyValue("catalogs")).thenReturn(categoryCatalogs);
        when(catMensRainBootsShoes.getPropertyValue("fixedParentCategories")).thenReturn(newSet(catMensShoesBoots, catMensShoesFootwear));

        // Men's Rain Shoes
        when(catMensRainShoes.getRepositoryId()).thenReturn("outdoorCat41110026");
        when(catMensRainShoes.getItemDisplayName()).thenReturn("Men's Rain Shoes");
        when(catMensRainShoes.getPropertyValue("catalogs")).thenReturn(categoryCatalogs);
        when(catMensRainShoes.getPropertyValue("fixedParentCategories")).thenReturn(newSet(catMensRainBootsShoes));

        // Men's Rain Boots
        when(catMensRainBoots.getRepositoryId()).thenReturn("outdoorCat41110025");
        when(catMensRainBoots.getItemDisplayName()).thenReturn("Men's Rain Boots");
        when(catMensRainBoots.getPropertyValue("catalogs")).thenReturn(categoryCatalogs);
        when(catMensRainBoots.getPropertyValue("fixedParentCategories")).thenReturn(newSet(catMensRainBootsShoes));

        // Snowshoe
        when(catSnowshoe.getRepositoryId()).thenReturn("outdoorCat11000003");
        when(catSnowshoe.getItemDisplayName()).thenReturn("Snowshoe");
        when(catSnowshoe.getPropertyValue("catalogs")).thenReturn(categoryCatalogs);
        when(catSnowshoe.getPropertyValue("fixedParentCategories")).thenReturn(newSet(catRoot));

        // Snowshoe Accessories
        when(catSnowshoeAccessories.getRepositoryId()).thenReturn("outdoorCat111000028");
        when(catSnowshoeAccessories.getItemDisplayName()).thenReturn("Snowshoe Accessories");
        when(catSnowshoeAccessories.getPropertyValue("catalogs")).thenReturn(categoryCatalogs);
        when(catSnowshoeAccessories.getPropertyValue("fixedParentCategories")).thenReturn(newSet(catSnowshoe));

        // Snowshoe Footwear
        when(catSnowshoeFootwear.getRepositoryId()).thenReturn("outdoorCat111100030");
        when(catSnowshoeFootwear.getItemDisplayName()).thenReturn("Snowshoe Footwear");
        when(catSnowshoeFootwear.getPropertyValue("catalogs")).thenReturn(categoryCatalogs);
        when(catSnowshoeFootwear.getPropertyValue("fixedParentCategories")).thenReturn(newSet(catSnowshoeAccessories));

        // Snowshoe boots
        when(catSnowshoeBoots.getRepositoryId()).thenReturn("outdoorCat111110031");
        when(catSnowshoeBoots.getItemDisplayName()).thenReturn("Snowshoe Boots");
        when(catSnowshoeBoots.getPropertyValue("catalogs")).thenReturn(categoryCatalogs);
        when(catSnowshoeBoots.getPropertyValue("fixedParentCategories")).thenReturn(newSet(catSnowshoeFootwear));

        when(prodMensBoot.getPropertyValue("parentCategories")).thenReturn(newSet(catMensRainShoes, catMensRainBoots, catSnowshoeBoots));

        // feed
        feed.setLoggingInfo(false);
        feed.setLoggingDebug(false);
        feed.setLoggingWarning(false);
        feed.setLoggingTrace(false);
        feed.setLoggingError(false);
    }

    private Set<RepositoryItem> newSet(RepositoryItem... items) {
        Set<RepositoryItem> set = new HashSet<RepositoryItem>(items.length);
        for (RepositoryItem item : items) {
            set.add(item);
        }
        return set;
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
        //verify(solrDocument, times(1)).addField("category", "4.outdoorCatalog.Snowshoe.Snowshoe Accessories.Snowshoe Footwear.Men's Rain Boots");

        verify(solrDocument, times(15)).addField(eq("category"), anyString());

        // verify leaf category ids
        verify(solrDocument, times(1)).addField("categoryId", "outdoorCat41110026");
        verify(solrDocument, times(1)).addField("categoryId", "outdoorCat41110025");
        verify(solrDocument, times(1)).addField("categoryId", "outdoorCat111110031");
        verify(solrDocument, times(3)).addField(eq("categoryId"), anyString());
    }
}

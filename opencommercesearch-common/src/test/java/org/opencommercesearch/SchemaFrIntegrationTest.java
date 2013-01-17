package org.opencommercesearch;

import atg.multisite.Site;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.util.NamedList;
import org.junit.Before;
import org.mockito.Mock;
import org.opencommercesearch.junit.SearchTest;

import java.util.ArrayList;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author rmerizalde
 */
public class SchemaFrIntegrationTest extends SchemaIntegrationTest {


    @Mock
    private Site site;

    @Mock
    private RepositoryItem catalog;

    @Before
    public void setUp() throws RepositoryException {
         initMocks(this);
         when(site.getRepositoryId()).thenReturn("outdoorSite");
         when(site.getPropertyValue("defaultCatalog")).thenReturn(catalog);
         when(catalog.getRepositoryId()).thenReturn("mycatalog");
    }

    @Override
    public Locale getLocale() {
        return Locale.FRENCH;
    }

    @SearchTest(language = "fr")
    public void testCategoryNameAnalyzer(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldType(server, "categoryName",
                "3.mycatalog.Vêtements pour Hommes.Vestes.Vestes Casual");

        for (ArrayList<NamedList<Object>> words : analysis.getWords()) {
            assertEquals("Failed validating word count: ", 2, words.size());
            assertEquals("vest", words.get(0).get("text"));
            assertEquals("casual", words.get(1).get("text"));
        }

        analysis = analyzeFieldType(server, "categoryName", "1.mycatalog.Vêtements pour Hommes");

        for (ArrayList<NamedList<Object>> words : analysis.getWords()) {
            assertEquals("Failed validating word count: ", 2, words.size());
            assertEquals("vêt", words.get(0).get("text"));
            assertEquals("homm", words.get(1).get("text"));
        }
    }

    @SearchTest(language = "fr")
    public void testCategoryNameTerms(SearchServer server) throws SearchServerException {
        SearchResponse res = server.termVector("north", getLocale(), "categoryName");
        NamedList<Object> analysis = res.getQueryResponse().getResponse();
        NamedList<Object> termVectors = (NamedList<Object>) analysis.get("termVectors");
        NamedList<Object> document = (NamedList<Object>) termVectors.get("PRD0001-SKU");
        NamedList<Object> field = (NamedList<Object>) document.get("categoryName");

        for (int i = 0; i < field.size(); ++i) {
            if (i % 2 == 0) {
                System.out.println(field.getVal(i));
            }
        }

        assertEquals("Failed validating term count: ", 3, field.size());
        assertEquals("Failed validating term frequency for 'category'", 3, ((NamedList<Object>) field.get("catégor")).get("tf"));
        assertEquals("Failed validating term frequency for 'two'", 1, ((NamedList<Object>) field.get("deux")).get("tf"));
        assertEquals("Failed validating term frequency for 'three'", 1, ((NamedList<Object>) field.get("trois")).get("tf"));
    }

    @SearchTest(language = "fr")
    public void testLowerCaseFilter(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "ceci est un TEST Français");

        for (ArrayList<NamedList<Object>> words : analysis.getWords()) {
            assertEquals("Failed validating word count: ", 2, words.size());
            assertEquals("test", words.get(0).get("text"));
            assertEquals("franc", words.get(1).get("text"));
        }
    }


}

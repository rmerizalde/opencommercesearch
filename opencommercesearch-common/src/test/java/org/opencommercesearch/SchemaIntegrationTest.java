package org.opencommercesearch;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import atg.multisite.Site;
import atg.repository.RepositoryItem;
import org.apache.solr.client.solrj.request.FieldAnalysisRequest;
import org.apache.solr.common.util.NamedList;
import org.junit.Before;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.opencommercesearch.junit.SearchTest;
import org.opencommercesearch.junit.runners.SearchJUnit4ClassRunner;

import java.util.ArrayList;

@Category(IntegrationSearchTest.class)
@RunWith(SearchJUnit4ClassRunner.class)
public class SchemaIntegrationTest {

    @Mock
    private Site site;

    @Mock
    private RepositoryItem catalog;

    @Before
    public void setUp() {
        initMocks(this);
        when(site.getRepositoryId()).thenReturn("mySite");
        when(site.getPropertyValue("defaultCatalog")).thenReturn(catalog);
        when(catalog.getRepositoryId()).thenReturn("mycatalog");
    }

    @SearchTest
    public void testCategoryNameAnalyzer(SearchServer server) throws SearchServerException {
        ArrayList<NamedList<Object>> words = analyzeFieldType(server, "categoryName",
                "3.mycatalog.Men's Clothing.Men's Jackets.Men's Casual Jackets");
        assertEquals("Failed validating word count: ", 3, words.size());
        assertEquals("men", words.get(0).get("text"));
        assertEquals("casual", words.get(1).get("text"));
        assertEquals("jacket", words.get(2).get("text"));
    }

    @SearchTest
    public void testCategoryNameTerms(SearchServer server) throws SearchServerException {
        SearchResponse res = server.termVector("north", "categoryName");
        NamedList<Object> analysis = res.getQueryResponse().getResponse();
        NamedList<Object> termVectors = (NamedList<Object>) analysis.get("termVectors");
        NamedList<Object> document = (NamedList<Object>) termVectors.get("PRD0001-SKU");
        NamedList<Object> field = (NamedList<Object>) document.get("categoryName");

        assertEquals("Failed validating term count: ", 4, field.size());
        assertEquals("Failed validating term frequency for 'category'", 3, ((NamedList<Object>) field.get("categori")).get("tf"));
        assertEquals("Failed validating term frequency for 'one'", 1, ((NamedList<Object>) field.get("on")).get("tf"));
        assertEquals("Failed validating term frequency for 'two'", 1, ((NamedList<Object>) field.get("two")).get("tf"));
        assertEquals("Failed validating term frequency for 'three'", 1, ((NamedList<Object>) field.get("three")).get("tf"));
    }

    private ArrayList<NamedList<Object>> analyzeFieldType(SearchServer server, String fieldType, String fieldValue)
            throws SearchServerException {
        FieldAnalysisRequest request = new FieldAnalysisRequest();
        request.addFieldType(fieldType);
        request.setFieldValue(fieldValue);

        return analyze(server, request, "field_types", fieldType);
    }

    private ArrayList<NamedList<Object>> analyzeFieldName(SearchServer server, String fieldName, String fieldValue)
            throws SearchServerException {
        FieldAnalysisRequest request = new FieldAnalysisRequest();
        request.addFieldName(fieldName);
        request.setFieldValue(fieldValue);

        return analyze(server, request, "field_names", fieldName);
    }

    private ArrayList<NamedList<Object>> analyze(SearchServer server, FieldAnalysisRequest request, String fields_key, String field_key)
            throws SearchServerException {
        NamedList<Object> res = server.analyze(request);
        NamedList<Object> analysis = (NamedList<Object>) res.get("analysis");
        NamedList<Object> fields = (NamedList<Object>) analysis.get(fields_key);
        NamedList<Object> field = (NamedList<Object>) fields.get(field_key);
        NamedList<Object> indexAnalysis = (NamedList<Object>) field.get("index");

        return (ArrayList<NamedList<Object>>) indexAnalysis.getVal(indexAnalysis.size() - 1);
    }
}

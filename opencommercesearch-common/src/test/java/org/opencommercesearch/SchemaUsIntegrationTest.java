package org.opencommercesearch;

import org.apache.solr.common.util.NamedList;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.opencommercesearch.junit.SearchTest;
import org.opencommercesearch.junit.runners.SearchJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

/**
 * @author rmerizalde
 */
@Category(IntegrationSearchTest.class)
@RunWith(SearchJUnit4ClassRunner.class)
public class SchemaUsIntegrationTest extends SchemaIntegrationTest {

    @Override
    public Locale getLocale() {
        return Locale.ENGLISH;
    }

    @SearchTest
    public void testCategoryNameAnalyzer(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldType(server, "categoryName",
                "3.mycatalog.Men's Clothing.Men's Jackets.Men's Casual Jackets");

        for (ArrayList<NamedList<Object>> words : analysis.getWords()) {
            assertEquals("Failed validating word count: ", 3, words.size());
            assertEquals("men", words.get(0).get("text"));
            assertEquals("casual", words.get(1).get("text"));
            assertEquals("jacket", words.get(2).get("text"));
        }
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

    @SearchTest
    public void testLowerCaseFilter(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "This is an English TEST");

        for (ArrayList<NamedList<Object>> words : analysis.getWords()) {
            assertEquals("Failed validating word count: ", 2, words.size());
            assertEquals("english", words.get(0).get("text"));
            assertEquals("test", words.get(1).get("text"));
        }
    }


}

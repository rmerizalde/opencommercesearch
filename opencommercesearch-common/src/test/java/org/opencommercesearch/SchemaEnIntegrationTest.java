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
public class SchemaEnIntegrationTest extends SchemaIntegrationTest {

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

    @SearchTest
    public void testWordDelimiterFilterApostrophe(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "O'neil's");
        ArrayList<NamedList<Object>> words = analysis.getQueryWords();

        assertEquals("Failed validating word count: ", 2, words.size());
        assertEquals("o", words.get(0).get("text"));
        assertEquals("neil", words.get(1).get("text"));

        words = analysis.getIndexWords();
        assertEquals("Failed validating word count: ", 3, words.size());
        assertEquals("o", words.get(0).get("text"));
        assertEquals("neil", words.get(1).get("text"));
        assertEquals("oneil", words.get(2).get("text"));
    }

    @SearchTest
    public void testWordDelimiterFilterDash(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "gore-tex");
        ArrayList<NamedList<Object>> words = analysis.getQueryWords();

        assertEquals("Failed validating word count: ", 2, words.size());
        assertEquals("gore", words.get(0).get("text"));
        assertEquals("tex", words.get(1).get("text"));

        words = analysis.getIndexWords();
        assertEquals("Failed validating word count: ", 3, words.size());
        assertEquals("gore", words.get(0).get("text"));
        assertEquals("tex", words.get(1).get("text"));
        assertEquals("goretex", words.get(2).get("text"));
    }

    @SearchTest
    public void testWordDelimiterFilterSlash(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "Norrøna 29/");

        for (ArrayList<NamedList<Object>> words : analysis.getWords()) {
            assertEquals("Failed validating word count: ", 2, words.size());
            assertEquals("norrøna", words.get(0).get("text"));
            assertEquals("29", words.get(1).get("text"));
        }
    }

    @SearchTest
    public void testWordDelimiterFilterNumbers(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "backpacks - 2440-2463cu in");
        ArrayList<NamedList<Object>> words = analysis.getQueryWords();

        assertEquals("Failed validating word count: ", 4, words.size());
        assertEquals("backpack", words.get(0).get("text"));
        assertEquals("2440", words.get(1).get("text"));
        assertEquals("2463", words.get(2).get("text"));
        assertEquals("cu", words.get(3).get("text"));

        words = analysis.getIndexWords();
        assertEquals("Failed validating word count: ", 5, words.size());
        assertEquals("backpack", words.get(0).get("text"));
        assertEquals("2440", words.get(1).get("text"));
        assertEquals("2463", words.get(2).get("text"));
        assertEquals("24402463", words.get(3).get("text"));
        assertEquals("cu", words.get(4).get("text"));
    }

    @SearchTest
    public void testWordDelimiterFilterSizes(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "Tires 26\"");

        for (ArrayList<NamedList<Object>> words : analysis.getWords()) {
            assertEquals("Failed validating word count: ", 2, words.size());
            assertEquals("tire", words.get(0).get("text"));
            assertEquals("26", words.get(1).get("text"));
        }

        analysis = analyzeFieldName(server, "text", "Tires 26x1.95");
        ArrayList<NamedList<Object>> words = analysis.getIndexWords();

        assertEquals("Failed validating word count: ", 6, words.size());
        assertEquals("tire", words.get(0).get("text"));
        assertEquals("26", words.get(1).get("text"));
        assertEquals("x", words.get(2).get("text"));
        assertEquals("1", words.get(3).get("text"));
        assertEquals("95", words.get(4).get("text"));
        assertEquals("195", words.get(5).get("text"));

        words = analysis.getIndexWords();

        assertEquals("Failed validating word count: ", 6, words.size());
        assertEquals("tire", words.get(0).get("text"));
        assertEquals("26", words.get(1).get("text"));
        assertEquals("x", words.get(2).get("text"));
        assertEquals("1", words.get(3).get("text"));
        assertEquals("95", words.get(4).get("text"));
    }

    @SearchTest
    public void testKeywordFilter(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "Alpinestars");

        for (ArrayList<NamedList<Object>> words : analysis.getWords()) {
            assertEquals("Failed validating word count: ", 1, words.size());
            assertEquals("alpinestars", words.get(0).get("text"));
        }
    }
}

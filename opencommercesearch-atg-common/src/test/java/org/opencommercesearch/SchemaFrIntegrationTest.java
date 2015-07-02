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

import atg.multisite.Site;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import org.junit.Before;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.opencommercesearch.junit.SearchTest;
import org.opencommercesearch.junit.runners.SearchJUnit4ClassRunner;

import java.util.List;
import java.util.Locale;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author rmerizalde
 */
@Category(IntegrationSearchTest.class)
@RunWith(SearchJUnit4ClassRunner.class)
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
    public void testLowerCaseFilter(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "ceci est un TEST Français");

        for (List<String> words : analysis.getWords()) {
            assertEquals("Failed validating word count: ", 2, words.size());
            assertThat(words, containsInAnyOrder("test", "francai"));
        }
    }

    @SearchTest(language = "fr")
    public void testElisionCaseFilter(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "l'enveloppe");

        for (List<String> words : analysis.getWords()) {
            assertEquals("Failed validating word count: ", 1, words.size());
            assertThat(words, containsInAnyOrder("envelop"));
        }

        analysis = analyzeFieldName(server, "text", "J'aime le lait");

        for (List<String> words : analysis.getWords()) {
            assertEquals("Failed validating word count: ", 2, words.size());
            assertThat(words, containsInAnyOrder("aime", "lait"));
        }
    }

    @SearchTest
    public void testWordDelimiterFilterApostrophe(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "O'neil's");
        List<String> words = analysis.getQueryWords();

        assertEquals("Failed validating word count: ", 3, words.size());
        assertThat(words, containsInAnyOrder("o", "neil", "s"));

        words = analysis.getIndexWords();
        assertEquals("Failed validating word count: ", 4, words.size());
        assertThat(words, containsInAnyOrder("o", "neil", "s", "oneil"));
    }

    @SearchTest
    public void testWordDelimiterFilterDash(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "gore-tex");
        List<String> words = analysis.getQueryWords();

        assertEquals("Failed validating word count: ", 2, words.size());
        assertThat(words, containsInAnyOrder("gore", "tex"));

        words = analysis.getIndexWords();
        assertEquals("Failed validating word count: ", 3, words.size());
        assertThat(words, containsInAnyOrder("gore", "goret", "tex"));
    }

    @SearchTest
    public void testWordDelimiterFilterSlash(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "Norrona 29/");

        for (List<String> words : analysis.getWords()) {
            assertEquals("Failed validating word count: ", 2, words.size());
            assertThat(words, containsInAnyOrder("norona", "29"));
        }
    }

    @SearchTest
    public void testWordDelimiterFilterNumbers(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "sacs à dos - 2440-2463cu cm");
        List<String> words = analysis.getQueryWords();

        assertEquals("Failed validating word count: ", 6, words.size());
        assertThat(words, containsInAnyOrder("sac", "dos", "2440", "2463", "cu", "cm"));

        words = analysis.getIndexWords();
        assertEquals("Failed validating word count: ", 7, words.size());
        assertThat(words, containsInAnyOrder("sac", "dos", "2440", "2463", "24402463", "cu", "cm"));
    }

    @SearchTest
    public void testWordDelimiterFilterSizes(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "Pneus 26\"");

        for (List<String> words : analysis.getWords()) {
            assertEquals("Failed validating word count: ", 2, words.size());
            assertThat(words, containsInAnyOrder("pneu", "26"));
        }

        analysis = analyzeFieldName(server, "text", "Pneus 26x1.95");
        List<String> words = analysis.getIndexWords();

        assertEquals("Failed validating word count: ", 6, words.size());
        assertThat(words, containsInAnyOrder("pneu", "26", "x", "1", "95", "195"));

        words = analysis.getIndexWords();

        assertEquals("Failed validating word count: ", 6, words.size());
        assertThat(words, containsInAnyOrder("pneu", "26", "x", "1", "95", "195"));
    }

    @SearchTest
    public void testKeywordFilter(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "Alpinestars");

        for (List<String> words : analysis.getWords()) {
            assertEquals("Failed validating word count: ", 1, words.size());
            assertThat(words, containsInAnyOrder("alpinesta"));
        }
    }
}

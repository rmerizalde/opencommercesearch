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
import org.apache.solr.common.util.NamedList;
import org.junit.Before;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.opencommercesearch.junit.SearchTest;
import org.opencommercesearch.junit.runners.SearchJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
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

        for (ArrayList<NamedList<Object>> words : analysis.getWords()) {
            assertEquals("Failed validating word count: ", 2, words.size());
            assertEquals("test", words.get(0).get("text"));
            assertEquals("francai", words.get(1).get("text"));
        }
    }

    @SearchTest(language = "fr")
    public void testElisionCaseFilter(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "l'enveloppe");

        for (ArrayList<NamedList<Object>> words : analysis.getWords()) {
            assertEquals("Failed validating word count: ", 1, words.size());
            assertEquals("envelop", words.get(0).get("text"));
        }

        analysis = analyzeFieldName(server, "text", "J'aime le lait");

        for (ArrayList<NamedList<Object>> words : analysis.getWords()) {
            assertEquals("Failed validating word count: ", 2, words.size());
            assertEquals("aime", words.get(0).get("text"));
            assertEquals("lait", words.get(1).get("text"));
        }
    }

    @SearchTest
    public void testWordDelimiterFilterApostrophe(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "O'neil's");
        ArrayList<NamedList<Object>> words = analysis.getQueryWords();

        assertEquals("Failed validating word count: ", 3, words.size());
        assertEquals("o", words.get(0).get("text"));
        assertEquals("neil", words.get(1).get("text"));
        assertEquals("s", words.get(2).get("text"));

        words = analysis.getIndexWords();
        assertEquals("Failed validating word count: ", 4, words.size());
        assertEquals("o", words.get(0).get("text"));
        assertEquals("neil", words.get(1).get("text"));
        assertEquals("s", words.get(2).get("text"));
        assertEquals("oneil", words.get(3).get("text"));
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
        assertEquals("goret", words.get(2).get("text"));
    }

    @SearchTest
    public void testWordDelimiterFilterSlash(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "Norrøna 29/");

        for (ArrayList<NamedList<Object>> words : analysis.getWords()) {
            assertEquals("Failed validating word count: ", 2, words.size());
            assertEquals("norøna", words.get(0).get("text"));
            assertEquals("29", words.get(1).get("text"));
        }
    }

    @SearchTest
    public void testWordDelimiterFilterNumbers(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "sacs à dos - 2440-2463cu cm");
        ArrayList<NamedList<Object>> words = analysis.getQueryWords();

        assertEquals("Failed validating word count: ", 6, words.size());
        assertEquals("sac", words.get(0).get("text"));
        assertEquals("dos", words.get(1).get("text"));
        assertEquals("2440", words.get(2).get("text"));
        assertEquals("2463", words.get(3).get("text"));
        assertEquals("cu", words.get(4).get("text"));
        assertEquals("cm", words.get(5).get("text"));

        words = analysis.getIndexWords();
        assertEquals("Failed validating word count: ", 7, words.size());
        assertEquals("sac", words.get(0).get("text"));
        assertEquals("dos", words.get(1).get("text"));
        assertEquals("2440", words.get(2).get("text"));
        assertEquals("2463", words.get(3).get("text"));
        assertEquals("24402463", words.get(4).get("text"));
        assertEquals("cu", words.get(5).get("text"));
        assertEquals("cm", words.get(6).get("text"));
    }

    @SearchTest
    public void testWordDelimiterFilterSizes(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "Pneus 26\"");

        for (ArrayList<NamedList<Object>> words : analysis.getWords()) {
            assertEquals("Failed validating word count: ", 2, words.size());
            assertEquals("pneu", words.get(0).get("text"));
            assertEquals("26", words.get(1).get("text"));
        }

        analysis = analyzeFieldName(server, "text", "Pneus 26x1.95");
        ArrayList<NamedList<Object>> words = analysis.getIndexWords();

        assertEquals("Failed validating word count: ", 6, words.size());
        assertEquals("pneu", words.get(0).get("text"));
        assertEquals("26", words.get(1).get("text"));
        assertEquals("x", words.get(2).get("text"));
        assertEquals("1", words.get(3).get("text"));
        assertEquals("95", words.get(4).get("text"));
        assertEquals("195", words.get(5).get("text"));

        words = analysis.getIndexWords();

        assertEquals("Failed validating word count: ", 6, words.size());
        assertEquals("pneu", words.get(0).get("text"));
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

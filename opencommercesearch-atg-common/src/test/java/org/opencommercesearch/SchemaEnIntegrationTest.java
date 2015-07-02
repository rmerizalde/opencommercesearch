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

import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.opencommercesearch.junit.SearchTest;
import org.opencommercesearch.junit.runners.SearchJUnit4ClassRunner;

import java.util.List;
import java.util.Locale;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

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
    public void testLowerCaseFilter(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "This is an English TEST");

        for (List<String> words : analysis.getWords()) {
            assertEquals("Failed validating word count: ", 2, words.size());
            assertThat(words, containsInAnyOrder("english", "test"));
        }
    }

    @SearchTest
    public void testWordDelimiterFilterApostrophe(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "O'neil's");
        List<String> words = analysis.getQueryWords();

        assertEquals("Failed validating word count: ", 2, words.size());
        assertThat(words, containsInAnyOrder("o", "neil"));

        words = analysis.getIndexWords();
        assertEquals("Failed validating word count: ", 3, words.size());
        assertThat(words, containsInAnyOrder("o", "neil", "oneil"));
    }

    @SearchTest
    public void testWordDelimiterFilterDash(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "gore-tex");
        List<String> words = analysis.getQueryWords();

        assertEquals("Failed validating word count: ", 2, words.size());
        assertThat(words, containsInAnyOrder("gore", "tex"));

        words = analysis.getIndexWords();
        assertEquals("Failed validating word count: ", 3, words.size());
        assertThat(words, containsInAnyOrder("gore", "goretex", "tex"));
    }

    @SearchTest
    public void testWordDelimiterFilterSlash(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "Norrona 29/");

        for (List<String> words : analysis.getWords()) {
            assertEquals("Failed validating word count: ", 2, words.size());
            assertThat(words, containsInAnyOrder("norrona", "29"));
        }
    }

    @SearchTest
    public void testWordDelimiterFilterNumbers(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "backpacks - 2440-2463cu in");
        List<String> words = analysis.getQueryWords();

        assertEquals("Failed validating word count: ", 4, words.size());
        assertThat(words, containsInAnyOrder("backpack", "2440", "2463", "cu"));

        words = analysis.getIndexWords();
        assertEquals("Failed validating word count: ", 5, words.size());
        assertThat(words, containsInAnyOrder("backpack", "2440", "2463", "24402463", "cu"));
    }

    @SearchTest
    public void testWordDelimiterFilterSizes(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "Tires 26\"");

        for (List<String> words : analysis.getWords()) {
            assertEquals("Failed validating word count: ", 3, words.size());
            assertThat(words, containsInAnyOrder("tires", "tire", "26"));
        }

        analysis = analyzeFieldName(server, "text", "Tires 26x1.95");
        List<String> words = analysis.getIndexWords();

        assertEquals("Failed validating word count: ", 7, words.size());
        assertThat(words, containsInAnyOrder("tires", "tire", "26", "x", "1", "95", "195"));

        words = analysis.getIndexWords();

        assertEquals("Failed validating word count: ",7, words.size());
        assertThat(words, containsInAnyOrder("tires", "tire", "26", "x", "1", "95", "195"));
    }

    @SearchTest
    public void testKeywordFilter(SearchServer server) throws SearchServerException {
        Analysis analysis = analyzeFieldName(server, "text", "Alpinestars");

        for (List<String> words : analysis.getWords()) {
            assertEquals("Failed validating word count: ", 1, words.size());
            assertThat(words, containsInAnyOrder("alpinestars"));
        }
    }
}

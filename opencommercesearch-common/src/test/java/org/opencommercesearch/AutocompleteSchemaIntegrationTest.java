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

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.request.FieldAnalysisRequest;
import org.apache.solr.client.solrj.response.AnalysisResponseBase;
import org.apache.solr.client.solrj.response.FieldAnalysisResponse;
import org.apache.solr.common.util.NamedList;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;

/**
 * @author rmerizalde
 */
@Category(IntegrationSearchTest.class)
public class AutocompleteSchemaIntegrationTest extends SolrTestCaseJ4 {

    private static EmbeddedSolrServer solrServer;

    @BeforeClass
    public static void setupSuite() throws Exception {
        Handler[] handlers = java.util.logging.Logger.getLogger("").getHandlers();
        ConsoleHandler consoleHandler = null;
        for (Handler handler : handlers) {
          if (handler instanceof ConsoleHandler) {
            consoleHandler = (ConsoleHandler)handler;
            break;
          }
        }
        consoleHandler.setLevel(Level.SEVERE);
        initCore("solrconfig.xml", "schema.xml", "solr", "autocomplete");
        solrServer = new EmbeddedSolrServer(h.getCore());
    }


    @Test
    public void testLowercase() throws Exception {
        FieldAnalysisRequest request = new FieldAnalysisRequest();

        request.addFieldType("normalizedQuery");
        request.setFieldValue("  MY search TERM  ");

        //FieldAnalysisRequest request = new FieldAnalysisRequest();
        //FieldAnalysisResponse response = request.process(solrServer);
        //FieldAnalysisResponse.Analysis analysis = response.getFieldTypeAnalysis("normalizedQuery");
        assertEquals("my search term", extractTextWords(solrServer.request(request)));
    }

    @Test
    public void testPunctuation() throws Exception {
        FieldAnalysisRequest request = new FieldAnalysisRequest();

        request.addFieldType("normalizedQuery");
        request.setFieldValue(" foo. bar t.baz b.a.z. ... foo.. 2.5 bar.");
        assertEquals("foo bar t.baz b.a.z. foo 2.5 bar", extractTextWords(solrServer.request(request)));
    }

    @Test
    public void testCommas() throws Exception {
        FieldAnalysisRequest request = new FieldAnalysisRequest();

        request.addFieldType("normalizedQuery");
        request.setFieldValue("foo,bar,, baz , bar bar,");
        assertEquals("foo bar baz bar bar", extractTextWords(solrServer.request(request)));
    }

    @Test
    public void testDoubleQuotes() throws Exception {
        FieldAnalysisRequest request = new FieldAnalysisRequest();

        request.addFieldType("normalizedQuery");
        request.setFieldValue("\"jacket\"");
        assertEquals("jacket", extractTextWords(solrServer.request(request)));

        request.setFieldValue("26\"");
        assertEquals("26\"", extractTextWords(solrServer.request(request)));

        request.setFieldValue("2.5  \"");
        assertEquals("2.5 \"", extractTextWords(solrServer.request(request)));

        request.setFieldValue("jacket \"red\"");
        assertEquals("jacket red", extractTextWords(solrServer.request(request)));

        request.setFieldValue("jacket \"\"\"red\"\"\"");
        assertEquals("jacket red", extractTextWords(solrServer.request(request)));
    }

    @Test
    public void testSingleQuotes() throws Exception {
        FieldAnalysisRequest request = new FieldAnalysisRequest();

        request.addFieldType("normalizedQuery");
        request.setFieldValue("kid's");
        assertEquals("kid's", extractTextWords(solrServer.request(request)));

        request.setFieldValue("kids'");
        assertEquals("kids", extractTextWords(solrServer.request(request)));

        request.setFieldValue("'jacket'");
        assertEquals("jacket", extractTextWords(solrServer.request(request)));

        request.setFieldValue("red''' ");
        assertEquals("red", extractTextWords(solrServer.request(request)));

        request.setFieldValue(" '''black''' ");
        assertEquals("black", extractTextWords(solrServer.request(request)));
    }

    @Test
    public void testMinus() throws Exception {
        FieldAnalysisRequest request = new FieldAnalysisRequest();

        request.addFieldType("normalizedQuery");
        request.setFieldValue("-40 ");
        assertEquals("-40", extractTextWords(solrServer.request(request)));

        request.setFieldValue("--40");
        assertEquals("-40", extractTextWords(solrServer.request(request)));

        request.setFieldValue("foo - bar");
        assertEquals("foo bar", extractTextWords(solrServer.request(request)));

        request.setFieldValue("foo -- bar");
        assertEquals("foo bar", extractTextWords(solrServer.request(request)));

        request.setFieldValue("foo--");
        assertEquals("foo-", extractTextWords(solrServer.request(request)));
    }

    @Test
    public void testAmpersandAndPercentage() throws Exception {
        FieldAnalysisRequest request = new FieldAnalysisRequest();

        request.addFieldType("normalizedQuery");
        request.setFieldValue("foo&bar");
        assertEquals("foo&bar", extractTextWords(solrServer.request(request)));

        request.setFieldValue("foo  & bar");
        assertEquals("foo & bar", extractTextWords(solrServer.request(request)));

        request.setFieldValue("40%");
        assertEquals("40%", extractTextWords(solrServer.request(request)));

        request.setFieldValue("40 %");
        assertEquals("40 %", extractTextWords(solrServer.request(request)));
    }

    @Test
    public void testNonAscii() throws Exception {
        // the schema currently support only 3 non-ascii characters ë ø ö
        FieldAnalysisRequest request = new FieldAnalysisRequest();

        request.addFieldType("normalizedQuery");
        request.setFieldValue("(#foo: bar(baz_foo)_ ) ");
        assertEquals("foo bar baz foo", extractTextWords(solrServer.request(request)));
    }

    @Test
    public void testRemoveOtherSpecialChars() throws Exception {
        // the schema currently support only 3 non-ascii characters ë ø ö
        FieldAnalysisRequest request = new FieldAnalysisRequest();

        request.addFieldType("normalizedQuery");
        request.setFieldValue("  lolë  norrøna völkl  ");
        assertEquals("lolë norrøna völkl", extractTextWords(solrServer.request(request)));
    }

    /**
     * Helper method to extract tokens from the last phase of the analysis
     *
     * Using weak type approach due to bug SOLR-2834
     *
     * https://issues.apache.org/jira/browse/SOLR-2834
     */
    private String extractTextWords(NamedList<Object> response) {
        NamedList<NamedList<NamedList<NamedList<Object>>>> analyis = (NamedList<NamedList<NamedList<NamedList<Object>>>>) response.get("analysis");
        NamedList<Object> phases = (NamedList<Object>) analyis.get("field_types").get("normalizedQuery").get("index");
        List<NamedList<Object>> tokenInfos = (List<NamedList<Object>>) phases.getVal(phases.size() - 1);
        StringBuffer textWords = new StringBuffer();

        for (NamedList<Object> tokenInfo : tokenInfos) {
            String type = (String) tokenInfo.get("type");

            if ("word".equals(type)) {
                textWords.append(tokenInfo.get("text")).append(' ');;
            }
        }

        if (textWords.length() > 0) {
            textWords.setLength(textWords.length() - 1);
        }
        return textWords.toString();
    }


    /**
     * Helper method to extract tokens from the last phase of the analysis
     */
    private String extractTextWords(FieldAnalysisResponse.Analysis analysis) {
        StringBuffer textWords = new StringBuffer();
        AnalysisResponseBase.AnalysisPhase lastPhase = null;

        for (AnalysisResponseBase.AnalysisPhase phase : analysis.getIndexPhases()) {
            lastPhase = phase;
        }

        for (AnalysisResponseBase.TokenInfo tokenInfo : lastPhase.getTokens()) {
            if ("word".equals(tokenInfo.getType())) {
                textWords.append(tokenInfo.getText()).append(' ');
            }
        }

        if (textWords.length() > 0) {
            textWords.setLength(textWords.length() - 1);
        }
        return textWords.toString();
    }
}

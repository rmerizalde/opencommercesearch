package org.opencommercesearch;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.request.FieldAnalysisRequest;
import org.apache.solr.client.solrj.response.AnalysisResponseBase;
import org.apache.solr.client.solrj.response.FieldAnalysisResponse;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;

/**
 * @author rmerizalde
 */
public class AutocompleteSchemaIntegrationTest extends SolrTestCaseJ4 {

    @BeforeClass
    public static void setupSuite() {
        Handler[] handlers = java.util.logging.Logger.getLogger("").getHandlers();
        ConsoleHandler consoleHandler = null;
        for (Handler handler : handlers) {
          if (handler instanceof ConsoleHandler) {
            consoleHandler = (ConsoleHandler)handler;
            break;
          }
        }
        consoleHandler.setLevel(Level.SEVERE);
    }

    @Before
    public void setupTest() throws Exception {
        initCore("solrconfig.xml", "schema.xml", "solr", "autocomplete");
    }

    @Test
    public void testLowercase() throws Exception {
        EmbeddedSolrServer solrServer = new EmbeddedSolrServer(h.getCore());
        FieldAnalysisRequest request = new FieldAnalysisRequest();

        request.addFieldType("normalizedQuery");
        request.setFieldValue("  MY search TERM  ");
        FieldAnalysisResponse response = request.process(solrServer);
        FieldAnalysisResponse.Analysis analysis = response.getFieldTypeAnalysis("normalizedQuery");

        assertEquals("my search term", extractTextWords(analysis));
    }

    private String extractTextWords(FieldAnalysisResponse.Analysis analysis) {
        StringBuffer textWords = new StringBuffer();
        AnalysisResponseBase.AnalysisPhase lastPhase = null;

        for (AnalysisResponseBase.AnalysisPhase phase : analysis.getIndexPhases()) {
            lastPhase = phase;
        }

        for (AnalysisResponseBase.TokenInfo tokenInfo : lastPhase.getTokens()) {
            if ("word".equals(tokenInfo.getType())) {
                textWords.append(tokenInfo.getText()).append(" ");
            }
        }

        if (textWords.length() > 0) {
            textWords.setLength(textWords.length() - 1);
        }
        return textWords.toString();
    }

}

package org.ecommercesearch;

import java.io.IOException;
import java.util.Collection;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;

/**
 * This interface represents a façade for a Solr server. Currently, it exposes
 * limited functionality but will be extended.
 * 
 * @author rmerizalde
 * 
 */
public interface SearchServer {

    UpdateResponse add(Collection<SolrInputDocument> docs) throws IOException, SolrServerException;

    UpdateResponse commit() throws IOException, SolrServerException;

    UpdateResponse deleteByQuery(String query) throws IOException, SolrServerException;

    SolrPingResponse ping() throws IOException, SolrServerException;

}

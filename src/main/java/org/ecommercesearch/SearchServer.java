package org.ecommercesearch;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;

import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;

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

    /**
     * This method can be used to notify the SearchServer that an item
     * descriptor was modified in the search repository. The server can analyze
     * the modification and take proper actions (export configuration, index
     * products, etc.)
     * 
     * 
     * @param repositoryName
     *            is the name of the repository where one or more items
     *            descriptor changed
     * @param itemDescriptorNames
     *            is a list of item descriptor names that were modified
     * @throws RepositoryException
     *             if an error occurs when accessing the repository
     * @throws SearchServerException
     *             if an exception occurs while taking some action. Most likely,
     *             this will be wrapping another exception depending of the
     *             implementation
     * 
     */
    void onRepositoryItemChanged(String repositoryName, Set<String> itemDescriptorNames) throws RepositoryException,
            SearchServerException;

    /**
     * TBD
     * 
     * @param product
     * @throws RepositoryException
     * @throws SearchServerException
     */
    void onProductChanged(RepositoryItem product) throws RepositoryException, SearchServerException;
}

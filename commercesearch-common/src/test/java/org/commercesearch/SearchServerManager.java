package org.commercesearch;

import atg.multisite.Site;
import atg.nucleus.ServiceException;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Set;
import java.util.logging.LogManager;
import java.util.logging.Logger;


/**
 * This class implements a manager to handle SearchServers (Solr cores) for the integration tests.
 *
 * By default, search test will use a read only server. However, this behavior can be overridden with
 * the {@code @SearchTest} annotation setting the newInstance attribute to true. In such case, the read only
 * server will be clone and the test can modify it. The cloned server gets destroyed when the test finishes running.
 *
 * @gsegura
 * @rmerizalde
 */
public class SearchServerManager {

    static {
        final InputStream inputStream = SearchServerManager.class.getResourceAsStream("/logging.properties");
        try
        {
            LogManager.getLogManager().readConfiguration(inputStream);
        }
        catch (final IOException e)
        {
            Logger.getAnonymousLogger().severe("Could not load default logging.properties file");
            Logger.getAnonymousLogger().severe(e.getMessage());
        }
    }

    private static EmbeddedSearchServer searchServer;
    private static SearchServer readOnlySearchServer;

    /**
     * Returns a readonly search server. Test will fail if they attempt to do updates on such server.
     * @return
     */
    public static SearchServer getSearchServer() {
        return getSearchServer(true, null, null, null);
    }

    /**
     * Returns a read-write instance which is a copy of the read only server.
     *
     * @param name the name to identify the new server (at its cores)
     *
     * @return a read-write search sever instance
     */
    public static SearchServer getSearchServer(String name) {
        return getSearchServer(false, name, null, null);
    }

    /**
     * Returns a read-write instance which is a copy of the read only server.
     *
     * @param name the name to identify the new server (at its cores)
     * @param productDataResource the URL to the XML product data resource
     * @param rulesDataResource the URL to the XML rules data resource
     *
     * @return a read-write search sever instance
     */
    public static SearchServer getSearchServer(String name, String productDataResource, String rulesDataResource) {
        return getSearchServer(false, name, productDataResource, rulesDataResource);
    }

    /**
     * Helper method to create a search server. If readonly is set to true, the read only instance is returned.
     * Otherwise, the read only instances is cloned and the given name is used to identify the new server.
     */
    private static SearchServer getSearchServer(boolean readOnly, String name, String productDataResource, String rulesDataResource) {
        if (searchServer == null) {
            initServer();
        }

        if (readOnly) {
            return readOnlySearchServer;
        }

        try {
            EmbeddedSearchServer copy = searchServer.createCopy(name);
            if (StringUtils.isNotBlank(productDataResource)) {
                copy.updateCollection(copy.getCatalogCollection(), productDataResource);
            }
            if (StringUtils.isNotBlank(rulesDataResource)) {
                copy.updateCollection(copy.getRulesCollection(), rulesDataResource);
            }
            return copy;
        } catch (SolrServerException ex) {
            throw new RuntimeException(ex);    
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Shutdowns a SearchServer clone through the getSearchServer method.
     *
     * @param server the server to shutdown
     */
    public static void shutdown(SearchServer server) {
        if (server instanceof EmbeddedSearchServer) {
            ((EmbeddedSearchServer) server).shutdownCores();
        }
    }

    /**
     * Helper method to initialize the read only search server. The ro server is a singleton.
     * If the test are configured to run in parallel multiple JVM will be spawn and each will
     * have its own read only server.
     */
    private static void initServer() {
        searchServer = new EmbeddedSearchServer();
        searchServer.setCatalogCollection("catalogPreview");
        searchServer.setRulesCollection("rulePreview");
        searchServer.setInMemoryIndex(true);
        searchServer.setEnabled(true);
        searchServer.setSolrConfigUrl("/solr/solr_preview.xml");
        searchServer.setSolrCorePath("solr");
        searchServer.setLoggingDebug(false);
        searchServer.setLoggingInfo(false);
        searchServer.setLoggingWarning(false);
        searchServer.setLoggingTrace(false);

        try {
            searchServer.doStartService();
            searchServer.updateCollection(searchServer.getCatalogCollection(), "/product_catalog/bootstrap.xml");
            searchServer.updateCollection(searchServer.getRulesCollection(), "/rules/bootstrap.xml");
            readOnlySearchServer = new ReadOnlySearchServer(searchServer);

        } catch (ServiceException ex) {
            throw new RuntimeException(ex);
        } catch (SolrServerException ex) {
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Simple wrapper class for read-only search server
     */
    private static class ReadOnlySearchServer implements SearchServer {
        private SearchServer server;

        ReadOnlySearchServer(SearchServer server) {
            this.server = server;
        }

        @Override
        public SearchResponse search(SolrQuery query, FilterQuery... filterQueries) throws SearchServerException {
            return server.search(query, filterQueries);
        }

        @Override
        public SearchResponse search(SolrQuery query, Site site, FilterQuery... filterQueries) throws SearchServerException {
            return server.search(query, site, filterQueries);
        }

        @Override
        public SearchResponse search(SolrQuery query, Site site, RepositoryItem catalog, FilterQuery... filterQueries) throws SearchServerException {
            return server.search(query, site, catalog, filterQueries);
        }

        @Override
        public UpdateResponse add(Collection<SolrInputDocument> docs) throws SearchServerException {
            throw new UnsupportedOperationException("Can't add a document to read only search server");
        }

        @Override
        public UpdateResponse commit() throws SearchServerException {
            throw new UnsupportedOperationException("Can't coommit on a read only search server");
        }

        @Override
        public UpdateResponse deleteByQuery(String query) throws SearchServerException {
            throw new UnsupportedOperationException("Can't delete documents in a read only search server");
        }

        @Override
        public SolrPingResponse ping() throws SearchServerException {
            return server.ping();
        }

        @Override
        public void onRepositoryItemChanged(String repositoryName, Set<String> itemDescriptorNames) throws RepositoryException, SearchServerException {
            throw new UnsupportedOperationException("Can't modify a search server");
        }

        @Override
        public void onProductChanged(RepositoryItem product) throws RepositoryException, SearchServerException {
            throw new UnsupportedOperationException("Can't modify a search server");
        }
    }


}

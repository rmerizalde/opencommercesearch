package org.opencommercesearch;

import atg.multisite.Site;
import atg.nucleus.ServiceException;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.DocumentAnalysisRequest;
import org.apache.solr.client.solrj.request.FieldAnalysisRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
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
            if (inputStream != null) {
                LogManager.getLogManager().readConfiguration(inputStream);
            }
        }
        catch (final IOException e)
        {
            Logger.getAnonymousLogger().severe("Could not load default logging.properties file");
            Logger.getAnonymousLogger().severe(e.getMessage());
        }
    }

    private static SearchServerManager searchServerManager;
    private EmbeddedSearchServer searchServer;
    private SearchServer readOnlySearchServer;

    private SearchServerManager() {}

    public static SearchServerManager getInstance() {
        if (searchServerManager == null) {
            searchServerManager = new SearchServerManager();
        }
        return searchServerManager;
    }

    /**
    * Check if there's an instance of the read-only or the read-write solr
    * server running
    *
    * @param roServer Specify which instance to check. If true will check if RO instance is running
    *
    * @return Indicate if the search server instance is running
    */
   public boolean isServerRunning(boolean roServer){
       if(roServer){
               return readOnlySearchServer != null;
       } else {
               return searchServer != null;
       }
   }

    /**
     * Returns a readonly search server. Test will fail if they attempt to do updates on such server.
     * @return
     */
    public SearchServer getSearchServer() {
        return getSearchServer(true, true, null, null, null);
    }

    /**
     * Returns a read-write instance which is a copy of the read only server.
     *
     * @param name the name to identify the new server (at its cores)
     *
     * @return a read-write search sever instance
     */
    public SearchServer getSearchServer(String name) {
        return getSearchServer(false, true, name, null, null);
    }

    /**
     * Returns a read-write instance which is a copy of the read only server.
     *
     * @param name the name to identify the new server (at its cores)
     *
     * @return a read-write search sever instance
     */
    public SearchServer getSearchServerWithEmptyIndex(String name) {
        return getSearchServer(false, false, name, null, null);
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
    public SearchServer getSearchServerWithResources(String name, String productDataResource, String rulesDataResource) {
        return getSearchServer(false, true, name, loadXmlResource(productDataResource),  loadXmlResource(rulesDataResource));
    }

    /**
     * Returns a read-write instance which is a copy of the read only server.
     *
     * @param name the name to identify the new server (at its cores)
     * @param productDataXml is a String with the XML data for products
     * @param rulesDataXml is a String with the XML data for rules
     *
     * @return a read-write search sever instance
     */
    public SearchServer getSearchServer(String name, String productDataXml, String rulesDataXml) {
        return getSearchServer(false, true, name, productDataXml,  rulesDataXml);
    }

    /**
     * Helper method to create a search server. If readonly is set to true, the read only instance is returned.
     * Otherwise, the read only instances is cloned and the given name is used to identify the new server.
     */
    private SearchServer getSearchServer(boolean readOnly, boolean loadBootstrapData, String name, String productDataResource, String rulesDataResource) {
        if (searchServer == null) {
            initServer(loadBootstrapData);
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
    public void shutdown(SearchServer server) {
            if (server instanceof EmbeddedSearchServer) {
            try {
                ((EmbeddedSearchServer) server).shutdownCores();
            } catch (SolrServerException ex) {
                throw new RuntimeException(ex);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }    
        }
    }

    /**
     * Shutdowns the singleton search server
     */
    public void shutdown() {
        if (searchServer != null) {
            shutdown(searchServer);
        }
        readOnlySearchServer = null;
    }

    /**
    * Initializes the read only search server. The ro server is a singleton.
    * If the tests are configured to run in parallel multiple JVMs will be spawn and each will
    * have its own read only server.
    *
    * The default data xml files are:
    *
    *   catalog: /product_catalog/bootstrap.xml
    *   rules: /rules/bootstrap.xml
    */
   public void initServer(boolean loadBootstrapData) {
       initServerAux(loadBootstrapData, loadXmlResource("/product_catalog/bootstrap.xml"), loadXmlResource("/rules/bootstrap.xml"));
   }

    /**
     * Initializes the read only search server. The ro server is a singleton.
     * If the test are configured to run in parallel multiple JVMs will be spawn and each will
     * have its own read only server.
     *
     * This signature allows to use custom catalog and rules xml files.
     */
    public void initServer(boolean loadBootstrapData,  String productDataXml, String rulesDataXml) {
        initServerAux(loadBootstrapData, productDataXml, rulesDataXml);
    }

    /**
     * Helper method to initialize the read only search server. The ro server is a singleton.
     * If the tests are configured to run in parallel multiple JVM will be spawn and each will
     * have its own read only server.
     */
    private void initServerAux(boolean loadBootstrapData, String productDataXml, String rulesDataXml) {
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
            if (loadBootstrapData) {
                if (productDataXml != null) {
                    searchServer.updateCollection(searchServer.getCatalogCollection(), productDataXml);
                }
                if (rulesDataXml != null) {
                    searchServer.updateCollection(searchServer.getRulesCollection(), rulesDataXml);
                }
            }
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
     * Helper method to load an XML resource into a String object
     */
    String loadXmlResource(String resourceName) {

        String out = null;

        if (StringUtils.isBlank(resourceName)){
            return null;
        }

        InputStream stream = null;

        try{
            stream = getClass().getResourceAsStream(resourceName);
            StringWriter writer = new StringWriter();

            IOUtils.copy(stream, writer);
            out = writer.getBuffer().toString();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
        return out;
    }

    public void updateCollection(SearchServer server, String collectionName, String xmlBody) {
        if (server instanceof EmbeddedSearchServer) {
            try {
                ((EmbeddedSearchServer) server).updateCollection(collectionName, xmlBody);
            } catch (SolrServerException ex) {
                throw new RuntimeException(ex);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            throw new UnsupportedOperationException("Unable to update server with type " + server.getClass().getName());
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
        public NamedList<Object> analyze(DocumentAnalysisRequest request) throws SearchServerException {
            return server.analyze(request);
        }

        @Override
        public NamedList<Object> analyze(FieldAnalysisRequest request) throws SearchServerException {
            return server.analyze(request);
        }

        @Override
        public SearchResponse termVector(String query, String... fields) throws SearchServerException {
            return server.termVector(query, fields);
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

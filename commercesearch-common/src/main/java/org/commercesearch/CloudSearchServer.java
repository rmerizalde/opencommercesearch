
package org.commercesearch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BinaryResponseParser;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.cloud.ClusterState;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.cloud.SolrZkClient;
import org.apache.solr.common.cloud.ZkCoreNodeProps;
import org.apache.solr.common.cloud.ZkNodeProps;
import org.apache.solr.common.cloud.ZkStateReader;
import org.apache.solr.common.params.CoreAdminParams.CoreAdminAction;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.commercesearch.repository.SearchRepositoryItemDescriptor;
import org.commercesearch.repository.SynonymListProperty;
import org.commercesearch.repository.SynonymProperty;

import atg.multisite.Site;
import atg.multisite.SiteContextManager;
import atg.nucleus.GenericService;
import atg.nucleus.ServiceException;
import atg.repository.Repository;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryView;
import atg.repository.rql.RqlStatement;

/**
 * The class implements SearchServer interface. This implementation is intended
 * for search clusters using SolrCloud.
 * 
 * Each target site should have its on search server configuration. They can
 * point to the same host but use different collections. Otherwise, the should
 * use a different host with either same or different collection name.
 * 
 * @author rmerizalde
 * 
 */
public class CloudSearchServer extends GenericService implements SearchServer {
    private static final BinaryResponseParser binaryParser = new BinaryResponseParser();

    // Current cloud implementation seem to have a bug. It support the
    // collection property but once a collection is used it sticks to it
    private CloudSolrServer catalogSolrServer;
    private CloudSolrServer ruleSolrServer;
    private SolrZkClient zkClient;
    private String host;
    private String catalogCollection;
    private String ruleCollection;
    private Repository searchRepository;
    private RqlStatement synonymRql;
    private RqlStatement ruleCountRql;
    private RqlStatement ruleRql;
    private int ruleBatchSize;
    private RuleManager ruleManager;

    public CloudSolrServer getSolrServer() {
        return catalogSolrServer;
    }

    public CloudSolrServer getSolrServer(String collection) {
        if (ruleCollection.equals(collection)) {
            return ruleSolrServer;
        }
        return catalogSolrServer;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getCatalogCollection() {
        return catalogCollection;
    }

    public void setCatalogCollection(String catalogCollection) {
        this.catalogCollection = catalogCollection;
    }

    public String getRuleCollection() {
        return ruleCollection;
    }

    public void setRuleCollection(String ruleCollection) {
        this.ruleCollection = ruleCollection;
    }

    public Repository getSearchRepository() {
        return searchRepository;
    }

    public void setSearchRepository(Repository searchRepository) {
        this.searchRepository = searchRepository;
    }

    public RqlStatement getSynonymRql() {
        return synonymRql;
    }

    public void setSynonymRql(RqlStatement synonymRql) {
        this.synonymRql = synonymRql;
    }

    public RqlStatement getRuleCountRql() {
        return ruleCountRql;
    }

    public void setRuleCountRql(RqlStatement ruleCountRql) {
        this.ruleCountRql = ruleCountRql;
    }

    public RqlStatement getRuleRql() {
        return ruleRql;
    }

    public void setRuleRql(RqlStatement ruleRql) {
        this.ruleRql = ruleRql;
    }

    public int getRuleBatchSize() {
        return ruleBatchSize;
    }

    public void setRuleBatchSize(int ruleBatchSize) {
        this.ruleBatchSize = ruleBatchSize;
    }

    private SolrZkClient getZkClient() {
        if (zkClient == null) {
            ZkStateReader stateReader = getSolrServer().getZkStateReader();

            if (stateReader == null) {
                try {
                    getSolrServer().ping();
                } catch (IOException ex) {
                    if (isLoggingDebug()) {
                        logDebug(ex);
                    }
                } catch (SolrServerException ex) {
                    if (isLoggingDebug()) {
                        logDebug(ex);
                    }
                }
                stateReader = getSolrServer().getZkStateReader();
            }

            if (stateReader != null) {
                zkClient = stateReader.getZkClient();
            }
        }

        if (zkClient == null && isLoggingWarning()) {
            logWarning("Unable to get Solr ZooKeeper Client");
        }
        return zkClient;
    }

    @Override
    public void doStartService() throws ServiceException {
        super.doStartService();
        initSolrServer();
    }

    public void initSolrServer() throws ServiceException {
        try {
            if (catalogSolrServer != null) {
                catalogSolrServer.shutdown();
            }
            catalogSolrServer = new CloudSolrServer(getHost());
            catalogSolrServer.setDefaultCollection(getCatalogCollection());
            if (ruleSolrServer != null) {
                ruleSolrServer.shutdown();
            }
            ruleSolrServer = new CloudSolrServer(getHost());
            ruleSolrServer.setDefaultCollection(getRuleCollection());
            ruleManager = new RuleManager(searchRepository, ruleSolrServer);
        } catch (MalformedURLException ex) {
            throw new ServiceException(ex);
        }
    }
    
    public QueryResponse search(SolrQuery query, String... filterQueries) throws SolrServerException {
        return search(query, SiteContextManager.getCurrentSite(), filterQueries);
    }

    public QueryResponse search(SolrQuery query, Site site, String... filterQueries) throws SolrServerException {
        return search(query, site, (RepositoryItem) site.getPropertyValue("defaultCatalog"), filterQueries);
    }

    public QueryResponse search(SolrQuery query, Site site, RepositoryItem catalog, String... filterQueries)
            throws SolrServerException {
        if (site == null) {
            throw new IllegalArgumentException("Missing site");
        }
        if (catalog == null) {
            throw new IllegalArgumentException("Missing catalog");
        }
        long startTime = System.currentTimeMillis();
        query.addFacetField("category");
        query.set("f.category.facet.mincount", 1);

        query.set("group", true);
        query.set("group.ngroups", true);
        query.set("group.limit", 50);
        query.set("group.field", "productId");
        query.set("group.facet", true);
        String categoryFilterQuery = setFilterQueries(query, filterQueries, catalog.getRepositoryId());
        try {
            ruleManager.setRuleParams(query, categoryFilterQuery);
        } catch (RepositoryException ex) {
            if (isLoggingError()) {
                logError("Unable to load search rules", ex);
            }
        } catch (SolrServerException ex) {
            if (isLoggingError()) {
                logError("Unable to load search rules", ex);
            }
        } finally {
            if (query.getSortFields() == null || query.getSortFields().length == 0) {
                query.addSortField("isToos", ORDER.asc);
                query.addSortField("score", ORDER.desc);
            }
        }
        QueryResponse res = getSolrServer().query(query);

        long searchTime = System.currentTimeMillis() - startTime;
        // @TODO change ths to debug mode
        if (isLoggingInfo()) {
            logInfo("Search time is " + searchTime + ", search engine time is " + res.getQTime());
        }
        return res;
    }

    private String setFilterQueries(SolrQuery query, String[] filterQueries, String catalogId) {
        String categoryFilterQuery = null;

        query.setFacetPrefix("1." + catalogId);
        query.addFilterQuery("category:" + "0." + catalogId);

        if (filterQueries == null) {
            return categoryFilterQuery;
        }

        for (final String filterQuery : filterQueries) {
            if (filterQuery.startsWith("category:")) {
                String[] parts = StringUtils.split(filterQuery, ':');
                if (parts.length > 0) {
                    String category = categoryFilterQuery = parts[1];
                    int index = category.indexOf(SearchConstants.CATEGORY_SEPARATOR);
                    if (index != -1) {
                        int level = Integer.parseInt(category.substring(0, index));
                        category = ++level + category.substring(index).replace("\\", "");

                        query.setFacetPrefix("category", category);
                    }
                }
            }
            query.addFilterQuery(filterQuery);
        }

        return categoryFilterQuery;
    }

    public UpdateResponse add(Collection<SolrInputDocument> docs) throws IOException, SolrServerException {
        return add(docs, getCatalogCollection());
    }

    public UpdateResponse add(Collection<SolrInputDocument> docs, String collection) throws IOException,
            SolrServerException {
        UpdateRequest req = new UpdateRequest();
        req.add(docs);
        req.setCommitWithin(-1);
        req.setParam("collection", collection);
        return req.process(getSolrServer(collection));
    }

    public SolrPingResponse ping() throws IOException, SolrServerException {
        return getSolrServer().ping();
    }

    public UpdateResponse commit() throws IOException, SolrServerException {
        return commit(getCatalogCollection());
    }

    public UpdateResponse commit(String collection) throws IOException, SolrServerException {
        UpdateRequest req = new UpdateRequest();
        req.setAction(UpdateRequest.ACTION.COMMIT, true, true);
        req.setParam("collection", collection);
        return req.process(getSolrServer(collection));
    }

    public UpdateResponse deleteByQuery(String query) throws IOException, SolrServerException {
        return deleteByQuery(query, getCatalogCollection());
    }

    public UpdateResponse deleteByQuery(String query, String collection) throws IOException, SolrServerException {
        UpdateRequest req = new UpdateRequest();
        req.deleteByQuery(query);
        req.setCommitWithin(-1);
        req.setParam("collection", collection);
        return req.process(getSolrServer(collection));
    }

    public void onRepositoryItemChanged(String repositoryName, Set<String> itemDescriptorNames)
            throws RepositoryException, SearchServerException {
        if (repositoryName.endsWith(getSearchRepository().getRepositoryName())) {
            if (itemDescriptorNames.contains(SearchRepositoryItemDescriptor.SYNONYM)
                    || itemDescriptorNames.contains(SearchRepositoryItemDescriptor.SYNONYM_LIST)) {
                try {
                    exportSynonyms();
                    reloadCollections();
                } catch (KeeperException ex) {
                    throw new SearchServerException("Exception exporting synonyms", ex);
                } catch (InterruptedException ex) {
                    throw new SearchServerException("Exception exporting synonyms", ex);
                }
            }
            if (itemDescriptorNames.contains(SearchRepositoryItemDescriptor.RULE)
                    || itemDescriptorNames.contains(SearchRepositoryItemDescriptor.BOOST_RULE)
                    || itemDescriptorNames.contains(SearchRepositoryItemDescriptor.BLOCK_RULE)) {
                try {
                    indexRules();
                } catch (IOException ex) {
                    throw new SearchServerException("Exception indexing rules", ex);
                } catch (SolrServerException ex) {
                    throw new SearchServerException("Exception indexing rules", ex);
                }
            }
        }
    }

    public void onProductChanged(RepositoryItem product) throws RepositoryException, SearchServerException {
        throw new UnsupportedOperationException();
    }

    /**
     * Export the synonym lists in the search repository to Zoo Keeper. Each
     * synonym list is exported into its own file. When renaming a new list or
     * creating its synonyms won't have effect until its get configured in an
     * analyzer.
     * 
     * When renaming a list that is currently being use by an analyzer it won't
     * be deleted to prevent the analyzer from breaking. However, new changes to
     * the renamed list won't take effect.
     * 
     * @throws RepositoryException
     *             when an error occurs while retrieving synonyms from the
     *             repository
     * @throws KeeperException
     *             if an error occurs while writing a synonym list to ZooKeeper
     * @throws InterruptedException
     *             if an error occurs while writing a synonym list to ZooKeeper
     */
    public void exportSynonyms() throws RepositoryException, KeeperException, InterruptedException {
        RepositoryView view = searchRepository.getView(SearchRepositoryItemDescriptor.SYNONYM_LIST);
        RepositoryItem[] synonymLists = getSynonymRql().executeQuery(view, null);
        if (synonymLists != null) {
            for (RepositoryItem synonymList : synonymLists) {
                exportSynonymList(synonymList);
            }
        } else {
            if (isLoggingInfo()) {
                logInfo("No synomym lists were exported to ZooKeeper");
            }
        }
    }

    /**
     * Exports the given synonym list into a configuration file in ZooKeeper
     * 
     * @param synonymList
     *            the synonym list's repository item
     * @throws KeeperException
     *             if a problem occurs while writing the file in ZooKeeper
     * @throws InterruptedException
     *             if a problem occurs while writing the file in ZooKeeper
     */
    private void exportSynonymList(RepositoryItem synonymList) throws KeeperException, InterruptedException {
        SolrZkClient client = getZkClient();

        if (client != null) {
            if (isLoggingInfo()) {
                logInfo("Exporting synoymym list '" + synonymList.getItemDisplayName() + "' to ZooKeeper");
            }
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            PrintWriter out = new PrintWriter(byteStream);

            out.println("# This file has been auto-generated. Do not modify");
            @SuppressWarnings("unchecked")
            Set<RepositoryItem> synonymMappings = (Set<RepositoryItem>) synonymList
                    .getPropertyValue(SynonymListProperty.MAPPINGS);
            for (RepositoryItem synonym : synonymMappings) {
                out.println((String) synonym.getPropertyValue(SynonymProperty.MAPPING));
            }
            out.close();

            for (String collection : Arrays.asList(getCatalogCollection(), getRuleCollection())) {
                byte[] data = byteStream.toByteArray();
                String path = new StringBuffer("/configs/").append(collection).append("/synonyms/")
                        .append(formatSynonymListFileName(synonymList.getItemDisplayName())).toString();

                if (!client.exists(path, true)) {
                    client.makePath(path, data, CreateMode.PERSISTENT, true);
                } else {
                    client.setData(path, data, true);
                }
            }
        }
    }

    /**
     * Reloads the catalog and rule collections
     * 
     * @throws SearchServerException
     */
    public void reloadCollections() throws SearchServerException {
        String collectionName = getCatalogCollection();
        try {
            reloadCollection(collectionName);
            collectionName = getRuleCollection();
            reloadCollection(collectionName);
        } catch (SolrServerException ex) {
            throw new SearchServerException("Exception reloading core " + collectionName, ex);
        } catch (IOException ex) {
            throw new SearchServerException("Exception reloading core " + collectionName, ex);
        } catch (InterruptedException ex) {
            throw new SearchServerException("Exception reloading core " + collectionName, ex);
        } catch (KeeperException ex) {
            throw new SearchServerException("Exception reloading core " + collectionName, ex);
        }
        
    }

    /**
     * Reloads the core
     * 
     * @param collectionName
     *            the cored to be reloaded
     * 
     * @throws SolrServerException
     *             if an error occurs while reloading the core for the synonyms
     *             to take effect
     * @throws IOException
     *             if an error occurs while reloading the core for the synonyms
     *             to take effect
     */
    public void reloadCollection(String collectionName) throws IOException, SolrServerException, KeeperException,
            InterruptedException {
        CoreAdminRequest adminRequest = new CoreAdminRequest();
        adminRequest.setCoreName(collectionName);
        adminRequest.setAction(CoreAdminAction.RELOAD);

        ClusterState clusterState = getSolrServer().getZkStateReader().getClusterState();
        Set<String> liveNodes = clusterState.getLiveNodes();

        if (liveNodes.size() == 0) {
            if (isLoggingInfo()) {
                logInfo("No live nodes found, 0 cores were reloaded");
            }
            return;
        }

        Map<String, Slice> slices = clusterState.getSlices(collectionName);
        if (slices.size() == 0) {
            if (isLoggingInfo()) {
                logInfo("No slices found, 0 cores were reloaded");
            }
        }

        for (Slice slice : slices.values()) {
            for (ZkNodeProps nodeProps : slice.getShards().values()) {
                ZkCoreNodeProps coreNodeProps = new ZkCoreNodeProps(nodeProps);
                String node = coreNodeProps.getNodeName();
                if (!liveNodes.contains(coreNodeProps.getNodeName())
                        || !coreNodeProps.getState().equals(ZkStateReader.ACTIVE)) {
                    if (isLoggingInfo()) {
                        logInfo("Node " + node + " is not live, unable to reload core " + collectionName);
                    }
                    continue;
                }

                if (isLoggingInfo()) {
                    logInfo("Reloading core " + collectionName + " on " + node);
                }
                HttpClient httpClient = getSolrServer().getLbServer().getHttpClient();
                HttpSolrServer nodeServer = new HttpSolrServer(coreNodeProps.getCoreUrl(), httpClient, binaryParser);
                CoreAdminResponse adminResponse = adminRequest.process(nodeServer);
                if (isLoggingInfo()) {
                    logInfo("Reladed core " + collectionName + ", current status is " + adminResponse.getCoreStatus());
                }
            }
        }
    }

    /**
     * Helper method to format a synonym list into a file name for storing in
     * ZooKeeper
     * 
     * @param synonymListName
     *            the name of the synonym list to format
     * @return the file name
     */
    private String formatSynonymListFileName(String synonymListName) {
        return StringUtils.replaceChars(synonymListName, ' ', '_').toLowerCase() + ".txt";
    }

    /**
     * Indexes all repository rules in the search index
     * 
     * @throws RepositoryException
     *             is an exception occurs while retrieving data from the
     *             repository
     * @throws SolrServerException
     *             if an exception occurs while indexing the document
     * @throws IOException
     *             if an exception occurs while indexing the document
     */
    public void indexRules() throws RepositoryException, SolrServerException, IOException {
        long startTime = System.currentTimeMillis();
        RepositoryView view = getSearchRepository().getView(SearchRepositoryItemDescriptor.RULE);
        int ruleCount = ruleCountRql.executeCountQuery(view, null);

        if (ruleCount == 0) {
            deleteByQuery("*:*", getRuleCollection());
            commit(getRuleCollection());
            if (isLoggingInfo()) {
                logInfo("No rules found for indexing");
            }
            return;
        }

        if (isLoggingInfo()) {
            logInfo("Started rule feed for " + ruleCount + " rules");
        }

        // TODO fix this
        deleteByQuery("*:*", getRuleCollection());

        List<SolrInputDocument> docs = new ArrayList<SolrInputDocument>();
        Integer[] rqlArgs = new Integer[] { 0, getRuleBatchSize() };
        RepositoryItem[] rules = ruleRql.executeQueryUncached(view, rqlArgs);

        int processed = 0;

        while (rules != null) {

            for (RepositoryItem rule : rules) {
                docs.add(ruleManager.createRuleDocument(rule));
                ++processed;
            }
            add(docs, getRuleCollection());
            commit(getRuleCollection());

            rqlArgs[0] += getRuleBatchSize();
            rules = ruleRql.executeQueryUncached(view, rqlArgs);

            if (isLoggingInfo()) {
                logInfo("Processed " + processed + " out of " + ruleCount);
            }
        }

        if (isLoggingInfo()) {
            logInfo("Rules feed finished in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds, "
                    + processed + " rules were indexed");
        }
    }

}

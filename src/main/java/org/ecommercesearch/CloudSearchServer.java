package org.ecommercesearch;

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
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BinaryResponseParser;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
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
import org.ecommercesearch.repository.RuleProperty;
import org.ecommercesearch.repository.SearchRepositoryItemDescriptor;
import org.ecommercesearch.repository.SynonymListProperty;
import org.ecommercesearch.repository.SynonymProperty;

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
            catalogSolrServer = new CloudSolrServer(getHost());
            catalogSolrServer.setDefaultCollection(getCatalogCollection());
            ruleSolrServer = new CloudSolrServer(getHost());
            ruleSolrServer.setDefaultCollection(getRuleCollection());
        } catch (MalformedURLException ex) {
            throw new ServiceException(ex);
        }
    }
    
    public UpdateResponse search(String query) {
        throw new UnsupportedOperationException();
    }

    public UpdateResponse search(String query, String siteId) {
        throw new UnsupportedOperationException();
    }

    public UpdateResponse search(String query, String siteId, String catalogId) {
        throw new UnsupportedOperationException();
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
            if (itemDescriptorNames.contains(SearchRepositoryItemDescriptor.RULE)) {
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
                docs.add(createRuleDocument(rule));
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

    /**
     * Create a search document representing a rule
     * 
     * @param rule
     *            the repository item to be indexed
     * @return the search document to be indexed
     * @throws RepositoryException
     *             is an exception occurs while retrieving data from the
     *             repository
     */
    private SolrInputDocument createRuleDocument(RepositoryItem rule) throws RepositoryException {
        SolrInputDocument doc = new SolrInputDocument();
        doc.setField("id", rule.getRepositoryId());
        String query = (String) rule.getPropertyValue(RuleProperty.QUERY);
        if (query == null || query.equals("*")) {
            query = "__all__";
        }
        doc.setField("query", query);

        @SuppressWarnings("unchecked")
        Set<RepositoryItem> sites = (Set<RepositoryItem>) rule.getPropertyValue(RuleProperty.SITES);

        if (sites != null && sites.size() > 0) {
            for (RepositoryItem site : sites) {
                doc.addField("siteId", site.getRepositoryId());
            }
        } else {
            doc.setField("siteId", "__all__");
        }

        @SuppressWarnings("unchecked")
        Set<RepositoryItem> catalogs = (Set<RepositoryItem>) rule.getPropertyValue(RuleProperty.CATALOGS);

        if (catalogs != null && sites.size() > 0) {
            for (RepositoryItem catalog : catalogs) {
                doc.addField("catalogId", catalog.getRepositoryId());
            }
        } else {
            doc.setField("catalogId", "__all__");
        }

        @SuppressWarnings("unchecked")
        Set<RepositoryItem> categories = (Set<RepositoryItem>) rule.getPropertyValue(RuleProperty.CATEGORIES);

        if (categories != null && categories.size() > 0) {
            for (RepositoryItem category : categories) {
                setCategoryId(doc, category);
            }
        } else {
            doc.setField("categoriId", "__all__");
        }

        return doc;
    }

    /**
     * Helper method to set the category id for document
     * 
     * @param doc
     *            the document to be indexed
     * @param category
     *            the category's repository item
     * @throws RepositoryException
     *             if an exception occurs while retrieving category info
     */
    private void setCategoryId(SolrInputDocument doc, RepositoryItem category) throws RepositoryException {
        if (!"category".equals(category.getItemDescriptor().getItemDescriptorName())) {
            return;
        }
        doc.addField("categoryId", category.getRepositoryId());
        @SuppressWarnings("unchecked")
        List<RepositoryItem> childCategories = (List<RepositoryItem>) category.getPropertyValue("childCategories");

        if (childCategories != null) {
            for (RepositoryItem childCategory : childCategories) {
                setCategoryId(doc, childCategory);
            }
        }
    }
}

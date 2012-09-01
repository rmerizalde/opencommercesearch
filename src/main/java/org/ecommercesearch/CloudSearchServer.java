package org.ecommercesearch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.cloud.SolrZkClient;
import org.apache.solr.common.cloud.ZkStateReader;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
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
    private CloudSolrServer solrServer;
    private SolrZkClient zkClient;
    private String host;
    private String collection;
    private Repository searchRepository;
    private RqlStatement synonymsRql;

    public CloudSolrServer getSolrServer() {
        return solrServer;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public Repository getSearchRepository() {
        return searchRepository;
    }

    public void setSearchRepository(Repository searchRepository) {
        this.searchRepository = searchRepository;
    }

    public RqlStatement getSynonymsRql() {
        return synonymsRql;
    }

    public void setSynonymsRql(RqlStatement synonymsRql) {
        this.synonymsRql = synonymsRql;
    }

    private SolrZkClient getZkClient() {
        if (zkClient == null) {
            ZkStateReader stateReader = solrServer.getZkStateReader();

            if (stateReader == null) {
                try {
                    solrServer.ping();
                } catch (IOException ex) {
                    if (isLoggingDebug()) {
                        logDebug(ex);
                    }
                } catch (SolrServerException ex) {
                    if (isLoggingDebug()) {
                        logDebug(ex);
                    }
                }
                stateReader = solrServer.getZkStateReader();
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
            solrServer = new CloudSolrServer(getHost());
            solrServer.setDefaultCollection(getCollection());
        } catch (MalformedURLException ex) {
            throw new ServiceException(ex);
        }
    }

    public UpdateResponse add(Collection<SolrInputDocument> docs) throws IOException, SolrServerException {
        return getSolrServer().add(docs);
    }

    public SolrPingResponse ping() throws IOException, SolrServerException {
        return solrServer.ping();
    }

    public UpdateResponse commit() throws IOException, SolrServerException {
        return getSolrServer().commit();
    }

    public UpdateResponse deleteByQuery(String query) throws IOException, SolrServerException {
        return getSolrServer().deleteByQuery(query);
    }

    public void onRepositoryItemChanged(String repositoryName, Set<String> itemDescriptorNames)
            throws RepositoryException, SearchServerException {
        if (repositoryName.endsWith(getSearchRepository().getRepositoryName())) {
            if (itemDescriptorNames.contains(SearchRepositoryItemDescriptor.SYNONYM)
                    || itemDescriptorNames.contains(SearchRepositoryItemDescriptor.SYNONYM_LIST)) {
                try {
                    exportSynonyms();
                } catch (KeeperException ex) {
                    throw new SearchServerException("Exception exporting synonyms", ex);
                } catch (InterruptedException ex) {
                    throw new SearchServerException("Exception exporting synonyms", ex);
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
        RepositoryItem[] synonymLists = synonymsRql.executeQuery(view, null);
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

            byte[] data = byteStream.toByteArray();
            String path = new StringBuffer("/configs/")
                .append(getCollection()).append("/synonyms/")
                .append(formatSynonymListFileName(synonymList.getItemDisplayName())).toString();

            if (!client.exists(path, true)) {
                client.makePath(path, data, CreateMode.PERSISTENT, true);
            } else {
                client.setData(path, data, true);
            }
        }
    }

    private String formatSynonymListFileName(String synonymListName) {
        return StringUtils.replaceChars(synonymListName, ' ', '_').toLowerCase() + ".txt";
    }
}

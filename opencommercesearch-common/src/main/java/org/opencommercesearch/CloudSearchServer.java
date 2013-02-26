
package org.opencommercesearch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.solr.client.solrj.ResponseParser;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BinaryResponseParser;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.common.cloud.ClusterState;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.cloud.SolrZkClient;
import org.apache.solr.common.cloud.ZkCoreNodeProps;
import org.apache.solr.common.cloud.ZkNodeProps;
import org.apache.solr.common.cloud.ZkStateReader;
import org.apache.solr.common.params.CoreAdminParams.CoreAdminAction;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.opencommercesearch.repository.SynonymListProperty;
import org.opencommercesearch.repository.SynonymProperty;

import static org.opencommercesearch.SearchServerException.create;
import static org.opencommercesearch.SearchServerException.Code.CORE_RELOAD_EXCEPTION;
import static org.opencommercesearch.SearchServerException.Code.EXPORT_SYNONYM_EXCEPTION;

import atg.nucleus.ServiceException;
import atg.repository.RepositoryItem;

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
public class CloudSearchServer extends AbstractSearchServer<CloudSolrServer> implements SearchServer {
    private static final BinaryResponseParser binaryParser = new BinaryResponseParser();
    // @TODO makes this configurable in the abstract search server
    private static final Locale[] SUPPORTED_LOCALES = {Locale.ENGLISH, Locale.FRENCH};

    private SolrZkClient zkClient;
    private String host;
    private ResponseParser responseParser = binaryParser;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public ResponseParser getResponseParser() {
        return responseParser;
    }

    public void setResponseParser(ResponseParser responseParser) {
        this.responseParser = responseParser;
    }

    private SolrZkClient getZkClient(Locale locale) {
        if (zkClient == null) {
            ZkStateReader stateReader = getCatalogSolrServer(locale).getZkStateReader();

            if (stateReader == null) {
                try {
                    getCatalogSolrServer(locale).ping();
                } catch (IOException ex) {
                    if (isLoggingDebug()) {
                        logDebug(ex);
                    }
                } catch (SolrServerException ex) {
                    if (isLoggingDebug()) {
                        logDebug(ex);
                    }
                }
                stateReader = getCatalogSolrServer(locale).getZkStateReader();
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
    
    protected void setSolrZkClient(SolrZkClient zkClient) {
        this.zkClient = zkClient;
    }

    @Override
    public void doStartService() throws ServiceException {
        super.doStartService();
        initSolrServer();
    }

    public void initSolrServer() throws ServiceException {
        try {
            for (Locale locale : SUPPORTED_LOCALES) {
                CloudSolrServer catalogSolrServer = getSolrServer(getCatalogCollection(), locale);
                String languagePrefix = "_" + locale.getLanguage();

                if (catalogSolrServer != null) {
                    catalogSolrServer.shutdown();
                }
                catalogSolrServer = new CloudSolrServer(getHost());
                catalogSolrServer.setDefaultCollection(getCatalogCollection() + languagePrefix);
                setCatalogSolrServer(catalogSolrServer, locale);

                CloudSolrServer rulesSolrServer = getSolrServer(getRulesCollection(), locale);

                if (rulesSolrServer != null) {
                    rulesSolrServer.shutdown();
                }
                rulesSolrServer = new CloudSolrServer(getHost());
                rulesSolrServer.setDefaultCollection(getRulesCollection() + languagePrefix);
                setRulesSolrServer(rulesSolrServer, locale);
            }
        } catch (MalformedURLException ex) {
            throw new ServiceException(ex);
        }
    }


    /**
     * Exports the given synonym list into a configuration file in ZooKeeper
     * 
     * @param synonymList
     *            the synonym list's repository item
     * @throws SearchServerException
     *             if a problem occurs while writing the file in ZooKeeper
     */
    protected void exportSynonymList(RepositoryItem synonymList, Locale locale) throws SearchServerException {
        SolrZkClient client = getZkClient(locale);

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

            for (String collection : Arrays.asList(getCatalogCollection(), getRulesCollection())) {
                byte[] data = byteStream.toByteArray();
                String path = new StringBuffer("/configs/").append(collection).append("/synonyms/")
                        .append(formatSynonymListFileName(synonymList.getItemDisplayName())).toString();

                try {
                    if (!client.exists(path, true)) {
                        client.makePath(path, data, CreateMode.PERSISTENT, true);
                    } else {
                        client.setData(path, data, true);
                    }
                } catch (KeeperException ex) {
                    throw create(EXPORT_SYNONYM_EXCEPTION, ex);
                } catch (InterruptedException ex) {
                    throw create(EXPORT_SYNONYM_EXCEPTION, ex);    
                }
            }
        }
    }

    /**
     * Reloads the core
     *
     * @param collectionName
     *            the cored to be reloaded
     *
     * @throws SearchServerException
     *          if an error occurs while reloading the core
     *
     */
    public void reloadCollection(String collectionName, Locale locale) throws SearchServerException
             {
        CoreAdminRequest adminRequest = new CoreAdminRequest();
        adminRequest.setCoreName(collectionName);
        adminRequest.setAction(CoreAdminAction.RELOAD);

        ClusterState clusterState = getSolrServer(collectionName, locale).getZkStateReader().getClusterState();
        Set<String> liveNodes = clusterState.getLiveNodes();

        if (liveNodes == null || liveNodes.size() == 0) {
            if (isLoggingInfo()) {
                logInfo("No live nodes found, 0 cores were reloaded");
            }
            return;
        }

        Map<String, Slice> slices = clusterState.getSlicesMap(collectionName);
        if (slices.size() == 0) {
            if (isLoggingInfo()) {
                logInfo("No slices found, 0 cores were reloaded");
            }
        }

        for (Slice slice : slices.values()) {
            for (ZkNodeProps nodeProps : slice.getReplicas()) {
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
                HttpClient httpClient = getSolrServer(collectionName, locale).getLbServer().getHttpClient();
                HttpSolrServer nodeServer = new HttpSolrServer(coreNodeProps.getCoreUrl(), httpClient, getResponseParser());
                try {
                    CoreAdminResponse adminResponse = adminRequest.process(nodeServer);
                    if (isLoggingInfo()) {
                        logInfo("Reladed core " + collectionName + ", current status is " + adminResponse.getCoreStatus());
                    }
                } catch (SolrServerException ex) {
                    throw create(CORE_RELOAD_EXCEPTION, ex);
                } catch (IOException ex) {
                    throw create(CORE_RELOAD_EXCEPTION, ex);    
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

}

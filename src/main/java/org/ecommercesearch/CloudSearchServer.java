package org.ecommercesearch;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collection;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;

import atg.nucleus.GenericService;
import atg.nucleus.ServiceException;

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
    private String host;
    private String collection;

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

}

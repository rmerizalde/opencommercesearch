package org.commercesearch;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.common.params.CoreAdminParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.SolrCore;
import org.apache.solr.logging.LoggerInfo;
import org.apache.solr.request.LocalSolrQueryRequest;
import org.xml.sax.SAXException;

import atg.nucleus.ServiceException;
import atg.repository.RepositoryItem;

/**
 * This class provides a SearchServer implementation which can be run as an embedded instance. By default, the configuration
 * is load from jar file. The component can be configured to read the configuration from the local
 *
 * @author gsegura
 * @author rmerizallde
 */
public class EmbeddedSearchServer extends AbstractSearchServer<EmbeddedSolrServer> {

    private String solrConfigUrl = "/solr/solr_preview.xml";
    private String solrCorePath = "solr";
    private String dataDir = "/opt/search/data";
    private boolean enabled = false;
    private boolean inMemoryIndex = false;
    
    private CoreContainer coreContainer;
    private File tmpConfigFile;
    
    public String getSolrConfigUrl() {
        return solrConfigUrl;
    }

    public void setSolrConfigUrl(String solrConfigUrl) {
        this.solrConfigUrl = solrConfigUrl;
    }    
    
    public String getSolrCorePath() {
        return solrCorePath;
    }

    public void setSolrCorePath(String solrCorePath) {
        this.solrCorePath = solrCorePath;
    }

    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean getInMemoryIndex() {
        return inMemoryIndex;
    }

    public void setInMemoryIndex(boolean inMemoryIndex) {
        this.inMemoryIndex = inMemoryIndex;
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    /**
     * Creates a copy of this search server by cloning the cores
     *
     * @param name is the name for the copy used to identify the new server's core, instance directory and other configurations
     *
     * @return a copy of this search server
     *
     * @throws SolrServerException if an error occurs while communicating with Solr
     * @throws IOException if an error occurs while communicating with Solr
     */
    public EmbeddedSearchServer createCopy(String name) throws SolrServerException, IOException {

        EmbeddedSearchServer copy = new EmbeddedSearchServer();

        String copyCatalogCoreName = name + "_" + getCatalogCollection();
        String copyRuleCoreName = name + "_" + getRuleCollection();

        copy.setCatalogCollection(copyCatalogCoreName);
        copy.setRuleCollection(copyRuleCoreName);
        cloneCore(getCatalogCollection(), copyCatalogCoreName, "product_catalog");
        cloneCore(getRuleCollection(), copyRuleCoreName, "rules");
        copy.setCatalogSolrServer(new EmbeddedSolrServer(coreContainer, copyCatalogCoreName));
        copy.setRulesSolrServer(new EmbeddedSolrServer(coreContainer, copyRuleCoreName));
        copy.setLoggingInfo(this.isLoggingInfo());
        copy.setLoggingDebug(this.isLoggingDebug());
        copy.setLoggingError(this.isLoggingError());
        copy.setLoggingWarning(this.isLoggingWarning());
        copy.coreContainer = coreContainer;

        return copy;
    }

    /**
     * Helper method to clone a core
     */
    private void cloneCore(String collectionName, String coreName, String instanceDir) throws SolrServerException, IOException {
        CoreAdminRequest.Create create = new CoreAdminRequest.Create();

        create.setCoreName(coreName);
        create.setInstanceDir(instanceDir);
        create.setDataDir(instanceDir + "/" + coreName + "/data");

        getSolrServer(collectionName).request(create);

        CoreAdminRequest.MergeIndexes mergeIndexes = new CoreAdminRequest.MergeIndexes();
        mergeIndexes.setCoreName(coreName);
        mergeIndexes.setSrcCores(Arrays.asList(collectionName));

        getSolrServer(collectionName).request(mergeIndexes);       
    }


    /**
     * Shutdown the cores for this server, however the coreContainer is left running. This method is intented for the
     * integration testing framework only. Don't use.
     */
    public void shutdownCores() {
        coreContainer.remove(getCatalogCollection());
        coreContainer.remove(getRuleCollection());
    }


    @Override
    public void doStartService() throws ServiceException {
        super.doStartService();
        try{            
            if(getEnabled()){
                long start = System.currentTimeMillis();
                String configUrl = getSolrConfigUrl();
                
                if(getInMemoryIndex()){
                    System.setProperty("solr.directoryFactory", "org.apache.solr.core.RAMDirectoryFactory");
                    System.setProperty("solr.lockFactory", "single");
                    configUrl += ".ram";
                } else {
                    System.setProperty("data.dir", getDataDir());
                    
                    if (!checkDataDirectory(getDataDir())) {
                        throw new ServiceException("Directory not found " + getDataDir());
                    }
                }
                
                InputStream in = getClass().getResourceAsStream(configUrl);

                if (in != null) {
                    tmpConfigFile = File.createTempFile("solr-", ".xml");

                    FileWriter out = new FileWriter(tmpConfigFile);

                    IOUtils.copy(in, out);
                    if (isLoggingInfo()) {
                        logInfo("Using embedded server with config file " + tmpConfigFile.getPath());
                    }
                    out.close();

                    long s = System.currentTimeMillis();
                    coreContainer = new CoreContainer(getSolrCorePath(), tmpConfigFile);
                    System.out.println("Init took " + ((System.currentTimeMillis() - start)));
                    setCatalogSolrServer(new EmbeddedSolrServer(coreContainer, getCatalogCollection()));
                    setRulesSolrServer(new EmbeddedSolrServer(coreContainer, getRuleCollection()));
                } else {
                    throw new ServiceException("Resource not found " + getSolrConfigUrl());
                }
            }
        } catch (SAXException ex) {
            if(isLoggingError()){
                logError(ex);
            }
        } catch (IOException ex) {
            if(isLoggingError()){
                logError(ex);
            }
        } catch (ParserConfigurationException ex) {
            if(isLoggingError()){
                logError(ex);
            }
        }
    }

    @Override
    public void doStopService() throws ServiceException {
        coreContainer.shutdown();
        tmpConfigFile.delete();
    }

    /**
     * Helper method to check if a directory structure exists
     */
    private boolean checkDataDirectory(String dataDir) {
        File file = new File(dataDir);
        boolean exists = true;

        if (!file.exists() ) {
            exists = file.mkdirs();

            if (isLoggingInfo()) {
                logInfo("Created data directory " + file.getPath());
            }
        }

        return exists;
    }
    
    @Override
    protected void exportSynonymList(RepositoryItem synonymList) throws SearchServerException {
        throw new UnsupportedOperationException("Exporting synonyms is only supported when using SolrCloud");        
    }

    @Override
    public void reloadCollection(String collectionName) throws SearchServerException {


        try {
            coreContainer.shutdown();
            coreContainer = new CoreContainer(getSolrCorePath(), tmpConfigFile);
            setCatalogSolrServer(new EmbeddedSolrServer(coreContainer, getCatalogCollection()));
            setRulesSolrServer(new EmbeddedSolrServer(coreContainer, getRuleCollection()));
        } catch (SAXException ex) {
            if(isLoggingError()){
                logError(ex);
            }
        } catch (IOException ex) {
            if(isLoggingError()){
                logError(ex);
            }
        } catch (ParserConfigurationException ex) {
            if(isLoggingError()){
                logError(ex);
            }
        }
    }

}

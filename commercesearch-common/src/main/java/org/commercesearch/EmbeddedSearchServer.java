package org.commercesearch;

import static org.commercesearch.SearchServerException.create;
import static org.commercesearch.SearchServerException.Code.CORE_RELOAD_EXCEPTION;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.core.CoreContainer;
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

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }    

    @Override
    public void doStartService() throws ServiceException {
        super.doStartService();
        try{            
            if(getEnabled()){
                InputStream in = getClass().getResourceAsStream(getSolrConfigUrl());

                if (in != null) {
                    tmpConfigFile = File.createTempFile("solr-", ".xml");

                    FileWriter out = new FileWriter(tmpConfigFile);

                    IOUtils.copy(in, out);
                    if (isLoggingInfo()) {
                        logInfo("Using embedded server with config file " + tmpConfigFile.getPath());
                    }
                    out.close();

                    if (!checkDataDirectory(getDataDir())) {
                        throw new ServiceException("Directory not found " + getDataDir());
                    }

                    System.setProperty("data.dir", getDataDir());
                    coreContainer = new CoreContainer(getSolrCorePath(), tmpConfigFile);
                    setCatalogSolrServer(new EmbeddedSolrServer(coreContainer, getCatalogCollection()));
                    setRulesSolrServer(new EmbeddedSolrServer(coreContainer, getRuleCollection()));
                    tmpConfigFile.deleteOnExit();
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

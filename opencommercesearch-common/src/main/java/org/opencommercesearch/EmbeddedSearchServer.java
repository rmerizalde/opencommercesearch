package org.opencommercesearch;

import atg.nucleus.ServiceException;
import atg.repository.RepositoryItem;
import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.request.DirectXmlRequest;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.SolrCore;
import org.omg.CORBA.LocalObject;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

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
    private String dataDir = null;
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

        if (isLoggingInfo()) {
            logInfo("Creating search server copy for " + name);
        }

        EmbeddedSearchServer copy = new EmbeddedSearchServer();

        // @TODO support for configurable locales
        for (Locale locale : new Locale[]{Locale.ENGLISH, Locale.FRENCH}) {
            String catalogCoreName = getCatalogCollection(locale);
            String rulesCoreName = getRulesCollection(locale);
            String copyCatalogCollectionName = name + "_" + getCatalogCollection();
            String copyRuleCollectionName = name + "_" + getRulesCollection();
            String copyCatalogCoreName = name + "_" + catalogCoreName;
            String copyRuleCoreName = name + "_" + rulesCoreName;
            SolrCore catalogCore = coreContainer.getCore(catalogCoreName);
            SolrCore rulesCore = coreContainer.getCore(rulesCoreName);

            if (catalogCore != null) {
                copy.setCatalogCollection(copyCatalogCollectionName);
                cloneCore(catalogCore, copyCatalogCollectionName, copyCatalogCoreName, "product_catalog", locale);
                copy.setCatalogSolrServer(createEmbeddedSolrServer(coreContainer, copyCatalogCollectionName, locale), locale);
                copy.getSolrServer(copyCatalogCollectionName, locale).commit();
            }

            if (rulesCore != null) {
                copy.setRulesCollection(copyRuleCollectionName);
                cloneCore(rulesCore, copyRuleCollectionName, copyRuleCoreName, "rules", locale);
                copy.setRulesSolrServer(createEmbeddedSolrServer(coreContainer, copyRuleCollectionName, locale), locale);
                copy.getSolrServer(copyRuleCollectionName, locale).commit();
            }
        }
        copy.setInMemoryIndex(getInMemoryIndex());
        copy.setEnabled(getEnabled());
        copy.setDataDir(getDataDir());
        copy.setSolrConfigUrl(getSolrConfigUrl());
        copy.setSolrCorePath(getSolrCorePath());
        copy.setLoggingInfo(this.isLoggingInfo());
        copy.setLoggingDebug(this.isLoggingDebug());
        copy.setLoggingError(this.isLoggingError());
        copy.setLoggingWarning(this.isLoggingWarning());
        copy.tmpConfigFile = tmpConfigFile;
        copy.coreContainer = coreContainer;

        if (isLoggingInfo()) {
            logInfo("Successfully create search server copy for " + name);
        }

        return copy;
    }

    /**
     * Helper method to clone a core
     */
    private void cloneCore(SolrCore core, String collectionName, String coreName, String instanceDir, Locale locale) throws SolrServerException, IOException {
        if (isLoggingInfo()) {
            logInfo("Cloning core '" + core.getName() + "' into '" + coreName + "' using instance directory " + instanceDir);
        }

        CoreAdminRequest.Create create = new CoreAdminRequest.Create();

        create.setCoreName(coreName);
        create.setInstanceDir(instanceDir);
        create.setDataDir(coreName + "/data");
        create.setSchemaName(core.getSchemaResource());
        getSolrServer(collectionName, locale).request(create);

        CoreAdminRequest.MergeIndexes mergeIndexes = new CoreAdminRequest.MergeIndexes();
        mergeIndexes.setCoreName(coreName);
        mergeIndexes.setSrcCores(Arrays.asList(core.getName()));

        SolrServer server = getSolrServer(collectionName, locale);
        server.request(mergeIndexes);

    }

    /**
     * Updates the collection with the given name with the XML contents
     * @param collectionName  the collection to update
     * @param locale of the collection to update
     * @param xmlBody The xml as a String
     *
     * @throws SolrServerException if an errors occurs while update the collection
     * @throws IOException if an errors occurs while update the collection
     */
    void updateCollection(String collectionName, String xmlBody, Locale locale) throws SolrServerException, IOException {
        if (isLoggingInfo()) {
            logInfo("Updating collection " + collectionName);
        }

        if (isLoggingDebug()) {
            logDebug("Updating collection " + collectionName + " with xml; " + xmlBody);
        }

        DirectXmlRequest request = new DirectXmlRequest("/update", xmlBody);
        SolrServer server = getSolrServer(collectionName, locale);

        server.request(request);
        server.commit();
    }

    /**
     * Shutdown the cores for this server, however the coreContainer is left running. This method is intented for the
     * integration testing framework only. Don't use.
     */
    public void shutdownCores() throws SolrServerException, IOException {
        if (isLoggingInfo()) {
            logInfo("Shutting down core for collection " + getCatalogCollection());
            logInfo("Shutting down core for collection " + getRulesCollection());
        }

        boolean deleteIndex = !getInMemoryIndex();
        // @TODO add support to shuutdown all localized cores
        CoreAdminRequest.unloadCore(getCatalogCollection(Locale.ENGLISH), deleteIndex, getCatalogSolrServer(Locale.ENGLISH));
        CoreAdminRequest.unloadCore(getRulesCollection(Locale.ENGLISH), deleteIndex, getRulesSolrServer(Locale.ENGLISH));
    }

    private EmbeddedSolrServer createEmbeddedSolrServer(final CoreContainer container, final String collectionName, final Locale locale) {
        String localizedCollectionName = collectionName + "_" + locale.getLanguage();
        return new EmbeddedSolrServer(container, localizedCollectionName);
    }


    @Override
    public void doStartService() throws ServiceException {
        super.doStartService();

        InputStream in = null;

        try{            
            long startTime = System.currentTimeMillis();

            String configUrl = getSolrConfigUrl();

            if(getInMemoryIndex()){
                System.setProperty("solr.directoryFactory", "org.apache.solr.core.RAMDirectoryFactory");
                System.setProperty("solr.lockFactory", "single");
                configUrl += ".ram";
                if (isLoggingInfo()) {
                    logInfo("Initializing in-memery embedded search server");
                }
            } else {
                if (getDataDir() != null) {
                    if (!checkDataDirectory(getDataDir())) {
                        throw new ServiceException("Directory not found " + getDataDir());
                    }
                    System.setProperty("data.dir", getDataDir());
                }

                if (isLoggingInfo()) {
                    logInfo("Initializing embedded search server, data directory is " + getDataDir());
                }
            }

            in = getClass().getResourceAsStream(configUrl);

            if (in != null) {
                tmpConfigFile = File.createTempFile("solr-", ".xml");

                FileWriter out = new FileWriter(tmpConfigFile);

                IOUtils.copy(in, out);
                if (isLoggingInfo()) {
                    logInfo("Using embedded sarch server with config file " + tmpConfigFile.getPath());
                }
                out.close();

                coreContainer = new CoreContainer(getSolrCorePath(), tmpConfigFile);
                // @TODO fix this support configurable supported locales
                setCatalogSolrServer(createEmbeddedSolrServer(coreContainer, getCatalogCollection(),  Locale.ENGLISH), Locale.ENGLISH);
                setRulesSolrServer(createEmbeddedSolrServer(coreContainer, getRulesCollection(), Locale.ENGLISH), Locale.ENGLISH);
                setCatalogSolrServer(createEmbeddedSolrServer(coreContainer, getCatalogCollection(), Locale.FRENCH), Locale.FRENCH);
                setRulesSolrServer(createEmbeddedSolrServer(coreContainer, getRulesCollection(), Locale.FRENCH), Locale.FRENCH);
            } else {
                throw new ServiceException("Resource not found " + getSolrConfigUrl());
            }

            if (isLoggingInfo()) {
                logInfo("Embedded search server initialized in " + (System.currentTimeMillis() - startTime) + "ms");
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
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ex) {
                if(isLoggingError()){
                    logError(ex);
                }
            }
        }

    }

    @Override
    public void doStopService() throws ServiceException {
        if (coreContainer != null) {
            coreContainer.shutdown();
        }
        if (tmpConfigFile != null) {
            tmpConfigFile.delete();
        }
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
    protected void exportSynonymList(RepositoryItem synonymList, Locale locale) throws SearchServerException {
        throw new UnsupportedOperationException("Exporting synonyms is only supported when using SolrCloud");        
    }

    @Override
    public void reloadCollection(String collectionName, Locale locale) throws SearchServerException {

        if (isLoggingInfo()) {
            logInfo("Reloading collection " + collectionName);
        }

        try {
            coreContainer.reload(collectionName);
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

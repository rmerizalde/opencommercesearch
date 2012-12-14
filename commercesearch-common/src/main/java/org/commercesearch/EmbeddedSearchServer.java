package org.commercesearch;

import static org.commercesearch.SearchServerException.create;
import static org.commercesearch.SearchServerException.Code.CORE_RELOAD_EXCEPTION;
import static org.commercesearch.SearchServerException.Code.UNSUPPORTED_METHOD_EXCEPTION;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.common.params.CoreAdminParams.CoreAdminAction;
import org.apache.solr.core.CoreContainer;
import org.xml.sax.SAXException;

import atg.nucleus.ServiceException;
import atg.repository.RepositoryItem;

public class EmbeddedSearchServer extends AbstractSearchServer<EmbeddedSolrServer> {

    private String solrCorePath = "/home/atguser/tmp/solr/apache-solr-4.0.0/commercesearch0/multicore";
    private boolean enabled = false;
    
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

    @Override
    public void doStartService() throws ServiceException {
        super.doStartService();
        try{            
            if(enabled){
                File home = new File( solrCorePath + "/solr.xml" );
                CoreContainer coreContainer = new CoreContainer(solrCorePath, home);     
                setCatalogSolrServer(new EmbeddedSolrServer(coreContainer, getCatalogCollection()));
                setRulesSolrServer(new EmbeddedSolrServer(coreContainer, getRuleCollection()));
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
    protected void exportSynonymList(RepositoryItem synonymList) throws SearchServerException {
        throw create(UNSUPPORTED_METHOD_EXCEPTION);        
    }

    @Override
    public void reloadCollection(String collectionName) throws SearchServerException {
        CoreAdminRequest adminRequest = new CoreAdminRequest();
        adminRequest.setCoreName(collectionName);
        adminRequest.setAction(CoreAdminAction.RELOAD);
        
        try {
            CoreAdminResponse adminResponse = adminRequest.process(getSolrServer(collectionName));
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

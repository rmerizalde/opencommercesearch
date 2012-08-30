package org.ecommercesearch.deployment;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.zookeeper.KeeperException;
import org.ecommercesearch.SearchServer;

import atg.deployment.common.Status;
import atg.deployment.common.event.DeploymentEvent;
import atg.deployment.common.event.DeploymentEventListener;
import atg.nucleus.GenericService;
import atg.nucleus.ServiceMap;

/**
 * This class implements DeploymentEventListener and should be added to the
 * DeploymentAgent configuration for all target sites. This class receives
 * deployments events and will push configuration or/and trigger catalog
 * indexing based on certain triggers.
 * 
 * This component can be configured to trigger in a specific deployment state.
 * Unless required, leave the default value (deployment complete).
 * 
 * Also, the component can be configured to be triggered for certain item
 * types... TBD
 * 
 * @author rmerizalde
 * 
 */
public class IndexingDeploymentListener extends GenericService implements DeploymentEventListener {

    private SearchServer searchServer;
    private String triggerStatus;
    private ServiceMap triggerItemTypes;

    public SearchServer getSearchServer() {
        return searchServer;
    }

    public void setSearchServer(SearchServer searchServer) {
        this.searchServer = searchServer;
    }

    public String getTriggerStatus() {
        return triggerStatus;
    }

    public void setTriggerStatus(String triggerStatus) {
        this.triggerStatus = triggerStatus;
    }

    public ServiceMap getTriggerItemTypes() {
        return triggerItemTypes;
    }

    public void setTriggerItemTypes(ServiceMap triggerItemTypes) {
        this.triggerItemTypes = triggerItemTypes;
    }

    @Override
    public void deploymentEvent(DeploymentEvent event) {
        if (getTriggerStatus().equals(Status.stateToString(event.getNewState()))) {
            if (isLoggingInfo()) {
                logInfo("Received event " + getTriggerStatus());
            }
            Map<String, String> affectedItemTypes = event.getAffectedItemTypes();
            logDebug(affectedItemTypes.toString());
            logDebug("" + event.getAffectedRepositories());
            // @TODO implement this
            if (affectedItemTypes != null) {
                // for (Entry<String, String> entry :
                // affectedItemTypes.entrySet()) {

                // Repository repository = (Repository)
                // triggerItemTypes.get(entry.getValue());
                // logDebug(repository.getRepositoryName());
                // if (repository != null &&
                // repository.getRepositoryName().equals(entry.getKey())) {
                // pushConfigurations(affectedItemTypes.values());
                // }
                // }
            }
        }
    }

    public void pushConfigurations(Collection<String> itemTypes) {
        if (isLoggingInfo()) {
            logInfo("Pushing search configurations for " + itemTypes);
        }
    }

    public void test() throws SolrServerException, IOException, KeeperException, InterruptedException {
        getSearchServer().ping();
        /*
         * ZkStateReader stateReader = solrServer.getZkStateReader(); if
         * (stateReader != null) { SolrZkClient client =
         * stateReader.getZkClient(); if (client != null) {
         * ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
         * OutputStreamWriter out = new OutputStreamWriter(byteStream);
         * 
         * out.write("Helo world!!\n"); out.write("This is a test!!!!");
         * out.close();
         * 
         * byte[] data = byteStream.toByteArray(); String path =
         * "/configs/catalog2/test.txt"; if (!client.exists(path, true)) {
         * client.makePath(path, data, CreateMode.PERSISTENT, true); } else {
         * client.setData(path, data, true); } } }
         */
    }


}

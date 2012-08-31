package org.ecommercesearch.deployment;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.zookeeper.KeeperException;
import org.ecommercesearch.SearchServer;

import atg.deployment.common.Status;
import atg.deployment.common.event.DeploymentEvent;
import atg.deployment.common.event.DeploymentEventListener;
import atg.nucleus.GenericService;
import atg.versionmanager.VersionManager;

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
    private List<String> triggerItemDescriptorNames;
    private VersionManager versionManager;

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

    public List<String> getTriggerItemDescriptorNames() {
        return triggerItemDescriptorNames;
    }

    public void setTriggerItemDescriptorNames(List<String> triggerItemDescriptorNames) {
        this.triggerItemDescriptorNames = triggerItemDescriptorNames;
    }

    @Override
    public void deploymentEvent(DeploymentEvent event) {
        if (getTriggerStatus().equals(Status.stateToString(event.getNewState()))) {
            Map<String, Set<String>> affectedItemTypes = event.getAffectedItemTypes();
            if (isLoggingDebug()) {
                logDebug("Received event " + getTriggerStatus() + " -> " + affectedItemTypes);
            }
            if (affectedItemTypes != null) {
                for (Entry<String, Set<String>> entry : affectedItemTypes.entrySet()) {
                    String repositoryName = entry.getKey();
                    Set<String> itemDescriptorNames = (Set<String>) entry.getValue();
                    for (String itemDescriptorName : itemDescriptorNames) {
                        if (triggerItemDescriptorNames.contains(repositoryName + ":" + itemDescriptorName)) {
                            pushConfigurations(repositoryName, itemDescriptorName);
                        }
                    }
                }
            }
        }
    }

    public void pushConfigurations(String repositoryName, String itemDescriptorName) {
        if (isLoggingInfo()) {
            logInfo("Pushing search configurations for repository '" + repositoryName + "' and item descriptor name '"
                    + itemDescriptorName + "'");
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

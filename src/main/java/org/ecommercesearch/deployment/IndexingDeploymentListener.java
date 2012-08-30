package org.ecommercesearch.deployment;

import java.io.IOException;
import java.util.Collection;
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
    private Set<String> triggerItemTypes;

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

    public Set<String> getTriggerItemTypes() {
        return triggerItemTypes;
    }

    public void setTriggerItemTypes(Set<String> triggerItemTypes) {
        this.triggerItemTypes = triggerItemTypes;
    }

    @Override
    public void deploymentEvent(DeploymentEvent event) {
        if (getTriggerStatus().equals(Status.stateToString(event.getNewState()))) {
            Map<String, String> affectedItemTypes = event.getAffectedItemTypes();
            if (affectedItemTypes != null) {
                for (Entry<String, String> entry : affectedItemTypes.entrySet()) {
                    String key = entry.getKey() + ":" + entry.getValue();
                    if (triggerItemTypes.contains(key)) {
                        pushConfigurations(affectedItemTypes.values());
                    }
                }
            }
        }
    }

    public void pushConfigurations(Collection<String> itemTypes) {
        if (isLoggingInfo()) {
            logInfo("Pushing search configurations for " + itemTypes);
        }
    }

    public void test() throws SolrServerException, IOException, KeeperException, InterruptedException {
        logDebug(">>>> ZK");
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

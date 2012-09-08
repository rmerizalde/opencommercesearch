package org.commercesearch.deployment;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.commercesearch.SearchServer;
import org.commercesearch.SearchServerException;

import atg.deployment.common.Status;
import atg.deployment.common.event.DeploymentEvent;
import atg.deployment.common.event.DeploymentEventListener;
import atg.nucleus.GenericService;
import atg.repository.RepositoryException;

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
                        if (isLoggingDebug()) {
                            logDebug("Processing " + itemDescriptorName + " for repository " + repositoryName);
                        }
                        if (triggerItemDescriptorNames.contains(repositoryName + ":" + itemDescriptorName)) {
                            notifyItemChange(repositoryName, itemDescriptorNames);
                            break;
                        }
                    }
                }
            }
        }
    }

    public void notifyItemChange(String repositoryName, Set<String> itemDescriptorNames) {
        if (isLoggingInfo()) {
            logInfo("Notifying search server of changes in repository " + repositoryName + " for item descriptors "
                    + itemDescriptorNames);
        }
        try {
            getSearchServer().onRepositoryItemChanged(repositoryName, itemDescriptorNames);
        } catch (RepositoryException ex) {
            if (isLoggingError()) {
                logError("Exception while processing deployemnt event for repository " + repositoryName
                        + "with item descriptors " + itemDescriptorNames, ex);
            }
        } catch (SearchServerException ex) {
            if (isLoggingError()) {
                logError("Exception while processing deployemnt event for repository " + repositoryName
                        + "with item descriptors " + itemDescriptorNames, ex);
            }
        }
    }
}

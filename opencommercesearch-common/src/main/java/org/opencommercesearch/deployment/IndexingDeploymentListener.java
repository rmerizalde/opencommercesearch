package org.opencommercesearch.deployment;

/*
* Licensed to OpenCommerceSearch under one
* or more contributor license agreements. See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership. OpenCommerceSearch licenses this
* file to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*/

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.opencommercesearch.SearchServer;
import org.opencommercesearch.SearchServerException;

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
 * @author nkumar
 * @author rmerizalde
 * 
 */
public class IndexingDeploymentListener extends GenericService implements DeploymentEventListener {

    private SearchServer searchServer;
    private String triggerStatus;
    private List<String> triggerItemDescriptorNames;
    private boolean enableEvaluation;
    private EvaluationServiceSender evaluationServiceSender;
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
    
    public EvaluationServiceSender getEvaluationServiceSender() {
        return evaluationServiceSender;
    }

    public void setEvaluationServiceSender(
            EvaluationServiceSender evaluationServiceSender) {
        this.evaluationServiceSender = evaluationServiceSender;
    }
  
     
    public boolean isEnableEvaluation() {
        return enableEvaluation;
    }

    public void setEnableEvaluation(boolean enableEvaluation) {
        this.enableEvaluation = enableEvaluation;
    }
    
    @Override
    public void deploymentEvent(DeploymentEvent event) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        String date = formatter.format(new Date());
        boolean doEvaluation = false;
        if (getTriggerStatus().equals(Status.stateToString(event.getNewState()))) {
            Map<String, Set<String>> affectedItemTypes = event.getAffectedItemTypes();
            if (isLoggingInfo()) {
                logInfo("Deployment event received " + getTriggerStatus() + " -> " + affectedItemTypes);
            }
            if (affectedItemTypes != null) {
                for (Entry<String, Set<String>> entry : affectedItemTypes.entrySet()) {
                    String repositoryName = entry.getKey();
                    Set<String> itemDescriptorNames = (Set<String>) entry.getValue();
                    for (String itemDescriptorName : itemDescriptorNames) {
                        if (isLoggingInfo()) {
                            logInfo("Processing " + itemDescriptorName + " for repository " + repositoryName);
                        }
                        if (triggerItemDescriptorNames.contains(repositoryName + ":" + itemDescriptorName)) {
                            doEvaluation = true;
                            notifyItemChange(repositoryName, itemDescriptorNames);
                            break;
                        }
                    }
                }
            }
        }
        
        if(isEnableEvaluation() && doEvaluation) {
        	if(isLoggingInfo()) {               
                logInfo("Sending Message for Evaluation Engine");
            }
            getEvaluationServiceSender().sendMessage("evaluate:"+date);
            if(isLoggingInfo()) {               
                logInfo("Message Sent forEvaluation Engine");
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

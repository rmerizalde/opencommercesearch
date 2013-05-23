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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.opencommercesearch.EvaluationServerConfig;
import org.opencommercesearch.EvaluationService;
import org.opencommercesearch.SearchServer;
import org.opencommercesearch.SearchServerException;
import org.opencommercesearch.service.SSHFileUploader;

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
    private EvaluationService evaluationService;
    private String beforeChangeFileName;
    private String afterChangeFileName;
    private SSHFileUploader sshFileUploader;
    private EvaluationServerConfig evaluationServerConfig;
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
    
    public EvaluationService getEvaluationService() {
        return evaluationService;
    }
    
    public void setEvaluationService(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }
     
    public String getBeforeChangeFileName() {
        return beforeChangeFileName;
    }
    
    public void setBeforeChangeFileName(String beforeChangeFileName) {
        this.beforeChangeFileName = beforeChangeFileName;
    }
    
    public String getAfterChangeFileName() {
        return afterChangeFileName;
    }
        
    public void setAfterChangeFileName(String afterChangeFileName) {
        this.afterChangeFileName = afterChangeFileName;
    }
    
    public SSHFileUploader getSshFileUploader() {
        return sshFileUploader;
    }
    
    public void setSshFileUploader(SSHFileUploader sshFileUploader) {
        this.sshFileUploader = sshFileUploader;
    }
    
    public EvaluationServerConfig getEvaluationServerConfig() {
        return evaluationServerConfig;
    }
    
    public void setEvaluationServerConfig(
        EvaluationServerConfig evaluationServerConfig) {
        this.evaluationServerConfig = evaluationServerConfig;
    }

    public void remoteCopyFileName(List<String> lst, String fileName) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStreamWriter out = new OutputStreamWriter(baos, "UTF-8");
            for (String element : lst) {
                out.write(element+"\n");
                out.flush();
            }
            
            byte[] bytes = baos.toByteArray();
            getSshFileUploader().uploadFile(fileName, bytes);
        } catch (IOException ex) {
            if (isLoggingInfo()) {
                logInfo("Not able to copy the file "+fileName+" on remote machine");
            }
        }
    }
	
	@Override
    public void deploymentEvent(DeploymentEvent event) {
        boolean hasAffectedSearch = false;		
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
                            if(!hasAffectedSearch) {
                                try {
                                    List<String> prevlists = getEvaluationService().generateSearchDocumentsList();
                                    if (isLoggingInfo()) {
                                        logInfo("Previous Documents lists size " + prevlists.size());
                                    }
                                    remoteCopyFileName(prevlists, getBeforeChangeFileName());		        
                                } catch (SearchServerException ex) {
                                    if (isLoggingInfo()) {
                                        logInfo("Unable to get documents lists " + ex.getMessage());
                                    }
                                }
                            }
                            hasAffectedSearch = true;	
                            notifyItemChange(repositoryName, itemDescriptorNames);
                            break;
                        }
                    }
                }
            }
        }
        
        if(hasAffectedSearch){
            try {
                List<String> currlists = getEvaluationService().generateSearchDocumentsList();
                remoteCopyFileName(currlists, getAfterChangeFileName());
                HttpClient client = new DefaultHttpClient();
                HttpGet get = new HttpGet("http://"+getEvaluationServerConfig().getServerHostName()+":"+getEvaluationServerConfig().getServerPort()+"/evaluation-engine/eval/"+getBeforeChangeFileName()+"/"+getAfterChangeFileName()+".txt");
                HttpResponse response = null;
                response = client.execute(get);
                if (isLoggingInfo()) {
                    logInfo("Response is " + response.getStatusLine());
                }
            } catch (SearchServerException ex) {
                if (isLoggingInfo()) {
                    logInfo("Unable to get documents lists " + ex.getMessage());
                }
            } catch (Exception ex) {
                if (isLoggingError()) {
                    logError("http://"+getEvaluationServerConfig().getServerHostName()+":"+getEvaluationServerConfig().getServerPort()+"/evaluation-engine/eval/"+getBeforeChangeFileName()+"/"+getAfterChangeFileName()+"Exception in getting response " + ex.getMessage());
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

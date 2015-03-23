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
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opencommercesearch.SearchServer;
import org.opencommercesearch.SearchServerException;

import atg.deployment.common.Status;
import atg.deployment.common.event.DeploymentEvent;
import atg.deployment.common.event.DeploymentEventListener;
import atg.nucleus.GenericService;
import atg.nucleus.ServiceException;
import atg.repository.RepositoryException;

import org.opencommercesearch.feed.FacetFeed;
import org.opencommercesearch.feed.RuleFeed;
import org.opencommercesearch.feed.CategoryFeed;

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
    private List<String> facetsTriggerItemDescriptorNames;
    private List<String> rulesTriggerItemDescriptorNames;
    private List<String> categoryTriggerItemDescriptorNames;
    private boolean enableEvaluation;
    private EvaluationServiceSender evaluationServiceSender;
    private ExecutorService executor;

    /**
     * Whether or not the indexing deployment listener is enabled.
     */
    private boolean enabled;

    /**
     * Facets REST feed instance.
     */
    private FacetFeed facetFeed;

    /**
     * Rules REST feed instance.
     */
    private RuleFeed ruleFeed;

    /**
     * Category REST feed instance
     */
    private CategoryFeed categoryFeed;

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

    public List<String> getFacetsTriggerItemDescriptorNames() {
        return facetsTriggerItemDescriptorNames;
    }

    public void setFacetsTriggerItemDescriptorNames(List<String> facetsTriggerItemDescriptorNames) {
        this.facetsTriggerItemDescriptorNames = facetsTriggerItemDescriptorNames;
    }

    public List<String> getRulesTriggerItemDescriptorNames() {
        return rulesTriggerItemDescriptorNames;
    }

    public void setRulesTriggerItemDescriptorNames(List<String> rulesTriggerItemDescriptorNames) {
        this.rulesTriggerItemDescriptorNames = rulesTriggerItemDescriptorNames;
    }

    public List<String> getCategoryTriggerItemDescriptorNames() {
        return categoryTriggerItemDescriptorNames;
    }

    public void setCategoryTriggerItemDescriptorNames(List<String> categoryTriggerItemDescriptorNames) {
        this.categoryTriggerItemDescriptorNames = categoryTriggerItemDescriptorNames;
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public FacetFeed getFacetFeed() {
        return facetFeed;
    }

    public void setFacetFeed(FacetFeed facetFeed) {
        this.facetFeed = facetFeed;
    }

    public RuleFeed getRuleFeed() {
        return ruleFeed;
    }

    public void setRuleFeed(RuleFeed ruleFeed) {
        this.ruleFeed = ruleFeed;
    }

    public void setCategoryFeed(CategoryFeed categoryFeed) {
        this.categoryFeed = categoryFeed;
    }

    public CategoryFeed getCategoryFeed() {
        return categoryFeed;
    }

    @Override
    public void doStartService() throws ServiceException {
        super.doStartService();
        executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public void doStopService() throws ServiceException {
        super.doStopService();
        executor.shutdown();
    }

    @Override
    public void deploymentEvent(DeploymentEvent event) {
        if(!isEnabled()) {
            if(isLoggingInfo()) {
                logInfo("Indexing deployment listener not enabled.");
            }
            return;
        }

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

                    boolean ruleRan = false;
                    boolean facetRan = false;
                    boolean categoryRan = false;
                    boolean genericRan = false;

                    for (String itemDescriptorName : itemDescriptorNames) {
                        if (isLoggingInfo()) {
                            logInfo("Processing " + itemDescriptorName + " for repository " + repositoryName);
                        }

                        String descriptorName = repositoryName + ":" + itemDescriptorName;
                        if(!ruleRan && getRulesTriggerItemDescriptorNames().contains(descriptorName)) {
                        	ruleRan = true;
                            executor.execute(new Runnable() {
                                public void run() {
                                    try {
                                        getRuleFeed().startFeed();
                                    }
                                    catch (Exception ex) {
                                        if(isLoggingError()) {
                                            logError("Rules REST feed failed", ex);
                                        }
                                    }
                                }
                            });
                        }

                        if(!facetRan && getFacetsTriggerItemDescriptorNames().contains(descriptorName)) {
                            try {
                                getFacetFeed().startFeed();
                                facetRan = true;
                            }
                            catch (Exception ex) {
                                if(isLoggingError()) {
                                    logError("Facet REST feed failed", ex);
                                }
                            }
                        }

                        if(!categoryRan && getCategoryTriggerItemDescriptorNames().contains(descriptorName)) {
                            try {
                                categoryFeed.startFeed();
                                categoryRan = true;
                            } catch (Exception ex) {
                                if (isLoggingError()) {
                                    logError("Category feed failed", ex);
                                }
                            }
                        }

                        if(!genericRan && triggerItemDescriptorNames.contains(descriptorName)) {
                            doEvaluation = true;
                            notifyItemChange(repositoryName, itemDescriptorNames);
                            genericRan = true;
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
                logError("Exception while processing deployment event for repository " + repositoryName
                        + "with item descriptors " + itemDescriptorNames, ex);
            }
        } catch (SearchServerException ex) {
            if (isLoggingError()) {
                logError("Exception while processing deployment event for repository " + repositoryName
                        + "with item descriptors " + itemDescriptorNames, ex);
            }
        }
        catch (Exception ex) {
            if (isLoggingError()) {
                logError("Unexpected exception while processing deployment event for repository " + repositoryName
                        + "with item descriptors " + itemDescriptorNames, ex);
            }
        }
    }
}

package org.opencommercesearch;

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

import static org.opencommercesearch.SearchServerException.create;
import static org.opencommercesearch.SearchServerException.Code.SEARCH_EXCEPTION;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.Group;
import org.apache.solr.client.solrj.response.GroupCommand;
import org.apache.solr.client.solrj.response.GroupResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.util.NamedList;

import atg.multisite.Site;
import atg.multisite.SiteContext;
import atg.multisite.SiteContextException;
import atg.multisite.SiteContextManager;
import atg.nucleus.GenericService;

/**
 * 
 * @author Naveen Kumar
 */
public class EvaluationService extends GenericService { 

    private SearchServer evaluationSearchServer;
    private SiteContextManager siteContextManager;
    private String queryFileName;
    private List<String> siteIds;

    public SearchServer getEvaluationSearchServer() {
        return evaluationSearchServer;
    }

    public void setEvaluationSearchServer(SearchServer evaluationSearchServer) {
        this.evaluationSearchServer = evaluationSearchServer;
    }
	
    public String getQueryFileName() {
        return queryFileName;
    }

    public void setQueryFileName(String queryFileName) {
        this.queryFileName = queryFileName;
    }

    public List<String> getSiteIds() {
        return siteIds;
    }

    public SiteContextManager getSiteContextManager() {
        return siteContextManager;
    }

    public void setSiteContextManager(SiteContextManager siteContextManager) {
        this.siteContextManager = siteContextManager;
    }

    public void setSiteIds(List<String> siteIds) {
        this.siteIds = siteIds;
    }

    public boolean existsQueryFile(String queryFile) {
        
        InputStream inputStream = getClass().getResourceAsStream(queryFile);
        if (inputStream != null) {
            return true;
        }
        if (isLoggingInfo()) {
            logInfo("Query File "+getQueryFileName()+" doesn't exists");
        }
        return false;
    }
	
    public List<String> generateSearchDocumentsList() throws SearchServerException {
        
        List<String> documentsList = new ArrayList<String>();
        for (String siteId : getSiteIds()) {
            if(!existsQueryFile(getQueryFileName())) {
                if (isLoggingInfo()) {
                    logInfo("Search Output File "+getQueryFileName()+" doesn't exists, can't generate output file");
                }
                continue;
            }
	    	
            Site site = null;
            SiteContext siteContext = null;
            try {
                site = getSiteContextManager().getSite(siteId);
                siteContext = siteContextManager.getSiteContext(site);
                siteContextManager.pushSiteContext(siteContext);
                InputStreamReader inputstream = new InputStreamReader(getClass().getResourceAsStream(getQueryFileName()));
                BufferedReader breader = new BufferedReader(inputstream);
                String line;
                while ((line = breader.readLine()) != null) {
                    SolrQuery query = new SolrQuery();
                    String tokens[]=line.trim().split(",",2);
                    query.setQuery(tokens[1].trim());
                    query.setRows(400);
                    query.setStart(0);
                    query.addFilterQuery("country:US");
                    SearchResponse searchResponse = getEvaluationSearchServer().search(query, site,(FilterQuery[]) null);
                    List<String> querydocList = getDocuments(tokens[0].trim(),searchResponse);
                    documentsList.addAll(querydocList);
                }
            } catch (SiteContextException ex) {
                if (isLoggingInfo()) {
                    logInfo("Unable to get site context " + ex.getMessage());
                }
                throw create(SEARCH_EXCEPTION, ex);
            } catch (SearchServerException ex) {
                if (isLoggingError()) {
                    logError("Unable to get search response or evaluation search server " + ex.getMessage());
                }
                throw create(SEARCH_EXCEPTION, ex);
            }
            catch (IOException ex) {
                if (isLoggingError()) {
                    logError("Unable to query file " + ex.getMessage());
                }
                throw create(SEARCH_EXCEPTION, ex);
            } catch (Exception ex) {
                logInfo("Exception " + ex.getMessage());
                throw create(SEARCH_EXCEPTION, ex);
            } 
            finally {
                if (siteContext != null) {
                    siteContextManager.popSiteContext(siteContext);
                }
            }
        }
        return documentsList;
    }
	
    public List<String> getDocuments(String queryNum, SearchResponse searchResponse){
        List<String> documents = new ArrayList<String>();
        int rankCounter=1;
        if (searchResponse != null) {
            QueryResponse queryResponse = searchResponse.getQueryResponse();
            GroupResponse groupResponse = queryResponse.getGroupResponse();
            if (groupResponse != null) {
                logInfo("GroupResponse "+groupResponse.getValues());
                for (GroupCommand command : groupResponse.getValues()) {
                    if ("productId".equals(command.getName())) {
                        for (Group group : command.getValues()) {
                            for (SolrDocument document : group.getResult()) {
                                if(!(Boolean)document.getFieldValue("isToos")) {
                                    documents.add(queryNum+" Q0 "+document.getFieldValue("id")+" "+rankCounter+" "+document.getFieldValue("score")+" currentrun");
                                    rankCounter++;
                                }
                            }
                        }
                    } else {
                        final int maxDocuments = 4;
                        Map<String, SolrDocument> uniqueDocuments = new HashMap<String, SolrDocument>(maxDocuments);
                        for (Group group : command.getValues()) {
                            if (group.getResult() == null || group.getResult().size() == 0) {
                                if (group.getResult() == null || group.getResult().size() == 0) {
                                    continue;
                                }                             
                                for (SolrDocument document : group.getResult()) {
                                    String productId = (String) document.getFieldValue("productId");
                                    if (uniqueDocuments.get(productId) == null) {
                                        if(!(Boolean)document.getFieldValue("isToos")) {
                                            documents.add(queryNum+" Q0 "+document.getFieldValue("id")+" "+rankCounter+" "+document.getFieldValue("score")+" currentrun");
                                            rankCounter++;
                                            uniqueDocuments.put(productId, document);
                                        }
                                    }
                                    if (uniqueDocuments.size() == maxDocuments) {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return documents;
    }
}
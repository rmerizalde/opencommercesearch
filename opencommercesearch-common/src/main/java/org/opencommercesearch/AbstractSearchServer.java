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

import atg.multisite.Site;
import atg.multisite.SiteContextManager;
import atg.nucleus.GenericService;
import atg.repository.Repository;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryView;
import atg.repository.rql.RqlStatement;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.DocumentAnalysisRequest;
import org.apache.solr.client.solrj.request.FieldAnalysisRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.GroupCommand;
import org.apache.solr.client.solrj.response.GroupResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.client.solrj.response.SpellCheckResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.GroupCollapseParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.SolrException;
import org.opencommercesearch.Facet.Filter;
import org.opencommercesearch.repository.RedirectRuleProperty;
import org.opencommercesearch.repository.SearchRepositoryItemDescriptor;

import static org.opencommercesearch.SearchServerException.create;
import static org.opencommercesearch.SearchServerException.Code.*;
import static org.opencommercesearch.SearchServerException.ExportSynonymException;

import java.io.IOException;
import java.util.*;

/**
 *
 *
 * @author gsegura
 * @author rmerizalde
 */
public abstract class AbstractSearchServer<T extends SolrServer> extends GenericService implements SearchServer {     // Current cloud implementation seem to have a bug. It support the
    // collection property but once a collection is used it sticks to it
    private Map<String, T> catalogSolrServers = new HashMap<String, T>();
    private Map<String, T> rulesSolrServers = new HashMap<String, T>();
    private String catalogCollection;
    private String rulesCollection;
    private String catalogConfig;
    private String rulesConfig;
    private String minimumMatch;
    private Repository searchRepository;
    private RqlStatement synonymListRql;
    private RqlStatement ruleCountRql;
    private RqlStatement ruleRql;
    private RqlStatement synonymRql;
    private int ruleBatchSize;
    private RulesBuilder rulesBuilder;

    public void setCatalogSolrServer(T catalogSolrServer, Locale locale) {
        catalogSolrServers.put(locale.getLanguage(), catalogSolrServer);
    }

    public T getCatalogSolrServer(Locale locale) {
        return catalogSolrServers.get(locale.getLanguage());
    }

    public void setRulesSolrServer(T rulesSolrServer, Locale locale) {
        rulesSolrServers.put(locale.getLanguage(), rulesSolrServer);
    }

    public T getRulesSolrServer(Locale locale) {
        return rulesSolrServers.get(locale.getLanguage());
    }

    public T getSolrServer(String collection, Locale locale) {
        if (rulesCollection != null && rulesCollection.equals(collection)) {
            return getRulesSolrServer(locale);
        }
        return getCatalogSolrServer(locale);
    }

    public String getCollectionName(String collection, Locale locale) {
        if (rulesCollection != null && rulesCollection.equals(collection)) {
            return getRulesCollection(locale);
        }
        return getCatalogCollection(locale);
    }

    public String getCatalogCollection() {
        return catalogCollection;
    }

    public String getCatalogCollection(Locale locale) {
        return catalogCollection + "_" + locale.getLanguage();
    }

    public void setCatalogCollection(String catalogCollection) {
        this.catalogCollection = catalogCollection;
    }

    public String getRulesCollection() {
        return rulesCollection;
    }

    public String getRulesCollection(Locale locale) {
        return rulesCollection + "_" + locale.getLanguage();
    }

    public String getMinimumMatch() {
        return minimumMatch;
    }

    public void setMinimumMatch(String minimumMatch) {
        this.minimumMatch = minimumMatch;
    }

    public String getCatalogConfig() {
        return catalogConfig;
    }

    public void setCatalogConfig(String catalogConfig) {
        this.catalogConfig = catalogConfig;
    }
    
    public String getRulesConfig() {
        return rulesConfig;
    }

    public void setRulesConfig(String rulesConfig) {
        this.rulesConfig = rulesConfig;
    }

    public void setRulesCollection(String ruleCollection) {
        this.rulesCollection = ruleCollection;
    }

    public Repository getSearchRepository() {
        return searchRepository;
    }

    public void setSearchRepository(Repository searchRepository) {
        this.searchRepository = searchRepository;
    }

    public RqlStatement getSynonymListRql() {
        return synonymListRql;
    }

    public void setSynonymListRql(RqlStatement synonymListRql) {
        this.synonymListRql = synonymListRql;
    }

    public RqlStatement getRuleCountRql() {
        return ruleCountRql;
    }

    public void setRuleCountRql(RqlStatement ruleCountRql) {
        this.ruleCountRql = ruleCountRql;
    }

    public RqlStatement getRuleRql() {
        return ruleRql;
    }

    public void setRuleRql(RqlStatement ruleRql) {
        this.ruleRql = ruleRql;
    }

    public RqlStatement getSynonymRql() {
        return synonymRql;
    }

    public void setSynonymRql(RqlStatement synonymRql) {
        this.synonymRql = synonymRql;
    }
    
    public int getRuleBatchSize() {
        return ruleBatchSize;
    }

    public void setRuleBatchSize(int ruleBatchSize) {
        this.ruleBatchSize = ruleBatchSize;
    }
        
    public RulesBuilder getRulesBuilder() {
        return rulesBuilder;
    }

    public void setRulesBuilder(RulesBuilder rulesBuilder) {
        this.rulesBuilder = rulesBuilder;
    }


    private static final String Q_ALT = "q.alt";
    private static final String BRAND_ID = "brandId";
    private static final String CATEGORY_PATH = "categoryPath";
    

    @Override
    public SearchResponse browse(BrowseOptions options, SolrQuery query, FilterQuery... filterQueries) throws SearchServerException {
        return browse(options, query, SiteContextManager.getCurrentSite(), Locale.US, filterQueries);
    }

    @Override
    public SearchResponse browse(BrowseOptions options, SolrQuery query, Locale locale, FilterQuery... filterQueries) throws SearchServerException {
        return browse(options, query, SiteContextManager.getCurrentSite(), locale, filterQueries);
    }

    @Override
    public SearchResponse browse(BrowseOptions options, SolrQuery query, Site site, FilterQuery... filterQueries) throws SearchServerException {
        return browse(options, query, SiteContextManager.getCurrentSite(), Locale.US, filterQueries);
    }

    @Override
    public SearchResponse browse(BrowseOptions options, SolrQuery query, Site site, Locale locale, FilterQuery... filterQueries)
            throws SearchServerException {

        boolean hasCategoryId = StringUtils.isNotBlank(options.getCategoryId());
        boolean hasCategoryPath = StringUtils.isNotBlank(options.getCategoryPath());
        boolean hasBrandId = StringUtils.isNotBlank(options.getBrandId());
        boolean addCategoryGraph = (options.isFetchCategoryGraph() || (hasBrandId && options.isFetchProducts())) && ! options.isRuleBasedPage();

        String categoryPath = null;
        
        if (options.isRuleBasedPage()) {
            //handle rule based pages
            String filter = rulesBuilder.buildRulesFilter(options.getCategoryId(), locale);
            query.set(Q_ALT, filter);
            query.setParam("q", "");
            
        } else {
            //handle brand, category or onsale pages
            if (hasCategoryPath) {
                categoryPath = options.getCategoryPath();
            } else {
                categoryPath = options.getCatalogId() + ".";
            }
    
            if (addCategoryGraph) {
                query.setFacetPrefix(CATEGORY_PATH, categoryPath);
                query.addFacetField(CATEGORY_PATH);
                query.set("f.categoryPath.facet.limit", options.getMaxCategoryResults());
            }
    
            if (!options.isFetchProducts()) {
                query.setRows(0);
            }
    
            List<String> queryAltParams = new ArrayList<String>();
    
            if (hasCategoryId) {
                queryAltParams.add(CATEGORY_PATH + ":" + categoryPath);
                query.setParam("q", "");
            }
    
            if (hasBrandId) {
                queryAltParams.add(BRAND_ID + ":" + options.getBrandId());
                query.setParam("q", "");
            }
    
            if (options.isOnSale()) {
                queryAltParams.add("onsale"+locale.getCountry()+":true");
            }
    
            if (queryAltParams.size() > 0) {
            	
                query.set(Q_ALT, "(" + StringUtils.join(queryAltParams, " AND ") + ")");
            }
        }

        RepositoryItem catalog = null;
        if (site != null) {
            catalog = (RepositoryItem) site.getPropertyValue("defaultCatalog");
        }
        
        SearchResponse response =  doSearch(query, site, catalog, locale, false, filterQueries);

        if (addCategoryGraph) {
            response.setCategoryGraph(createCategoryGraph(response,
                    options.getCategoryPath(), options.getCatalogId(),
                    options.getCategoryId()));
        }

        return response;
    }
    
    
    @Override
    public SearchResponse search(SolrQuery query, FilterQuery... filterQueries) throws SearchServerException {
        return search(query, SiteContextManager.getCurrentSite(), Locale.US, filterQueries);
    }

    @Override
    public SearchResponse search(SolrQuery query, Locale locale, FilterQuery... filterQueries) throws SearchServerException {
        return search(query, SiteContextManager.getCurrentSite(), locale, filterQueries);
    }

    @Override
    public SearchResponse search(SolrQuery query, Site site, FilterQuery... filterQueries) throws SearchServerException {
        return search(query, site, Locale.US, filterQueries);
    }

    @Override
    public SearchResponse search(SolrQuery query, Site site, Locale locale, FilterQuery... filterQueries) throws SearchServerException {
        RepositoryItem catalog = null;
        if (site != null) {
            catalog = (RepositoryItem) site.getPropertyValue("defaultCatalog");
        }
        return search(query, site, catalog, locale, filterQueries);
    }

    @Override
    public SearchResponse search(SolrQuery query, Site site, RepositoryItem catalog, FilterQuery... filterQueries)
            throws SearchServerException {
        return search(query, site, catalog, Locale.ENGLISH, filterQueries);
    }

    @Override
    public SearchResponse search(SolrQuery query, Site site, RepositoryItem catalog, Locale locale, FilterQuery... filterQueries)
            throws SearchServerException {
        return doSearch(query, site, catalog, locale, true, filterQueries);
    }
            
    private SearchResponse doSearch(SolrQuery query, Site site, RepositoryItem catalog, Locale locale, boolean isSearch, FilterQuery... filterQueries)
            throws SearchServerException {
        if (site == null) {
            throw new IllegalArgumentException("Missing site");
        }
        if (catalog == null) {
            throw new IllegalArgumentException("Missing catalog");
        }
        long startTime = System.currentTimeMillis();
        
        query.addFacetField("category");
        query.set("facet.mincount", 1);
        
        RuleManager ruleManager = new RuleManager(getSearchRepository(), getRulesBuilder(), getRulesSolrServer(locale));
        if (query.getRows() != null && query.getRows() > 0) {
            setGroupParams(query);
            setFieldListParams(query, locale.getCountry(), catalog.getRepositoryId());
            try {
                ruleManager.setRuleParams(filterQueries, catalog, query, isSearch);
                
                if(ruleManager.getRules().containsKey(SearchRepositoryItemDescriptor.REDIRECT_RULE)){
                    Map<String, List<RepositoryItem>> rules = ruleManager.getRules();
                    List<RepositoryItem> redirects = rules.get(SearchRepositoryItemDescriptor.REDIRECT_RULE);
                    if(redirects != null){
                        RepositoryItem redirect = redirects.get(0);
                        return new SearchResponse(query, null, null, null, (String) redirect.getPropertyValue(RedirectRuleProperty.URL), null, true);
                    }
                }
                
            } catch (RepositoryException ex) {
               if (isLoggingError()) {
                    logError("Unable to load search rules: " + ex.getMessage());
               }
               throw create(SEARCH_EXCEPTION, ex);
            } catch (SolrServerException ex) {
               if (isLoggingError()) {
                   logError("Unable to load search rules: " + ex.getMessage());
               }
               throw create(SEARCH_EXCEPTION, ex);
            } catch (SolrException ex) {
                if (isLoggingError()) {
                   logError("Unable to load search rules: " + ex.getMessage());
                }
                throw create(SEARCH_EXCEPTION, ex);
            }
        }
        try {
            QueryResponse queryResponse = getCatalogSolrServer(locale).query(query);

            String correctedTerm = null;
            boolean matchesAll = true;
            
            //if no results, check for spelling errors
            if(query.getRows() > 0 && isEmptySearch(queryResponse.getGroupResponse()) && StringUtils.isNotEmpty(query.getQuery())){
                
                SpellCheckResponse spellCheckResponse = queryResponse.getSpellCheckResponse();
                //try to do searching for the corrected term matching all terms (q.op=AND)
                QueryResponse tentativeResponse = handleSpellCheck(spellCheckResponse, getCatalogSolrServer(locale), query, "AND");
                if(tentativeResponse != null) {
                    //if we got results, set the corrected term variable and proceed to return the results
                    queryResponse = tentativeResponse;                    
                    correctedTerm = spellCheckResponse.getCollatedResult();
                } else {
                    //if we didn't got any response, try doing another search matching any term (q.op=OR)
                    tentativeResponse = handleSpellCheck(spellCheckResponse, getCatalogSolrServer(locale), query, "OR");
                    if(tentativeResponse != null) {
                        //if we got results for the match any term scenario. Set similar results to true
                        //and set the corrected term.
                        queryResponse = tentativeResponse;
                        matchesAll = false;
                        correctedTerm = query.getQuery();
                    }
                }

            }

            long searchTime = System.currentTimeMillis() - startTime;
         // @TODO change ths to debug mode
            if (isLoggingInfo()) {
                logInfo("Search time is " + searchTime + ", search engine time is " + queryResponse.getQTime());
            }
            return new SearchResponse(query, queryResponse, ruleManager, filterQueries, null, correctedTerm, matchesAll);
        } catch (SolrServerException ex) {
            throw create(SEARCH_EXCEPTION, ex);
        } catch (SolrException ex) {
             throw create(SEARCH_EXCEPTION, ex);
        }

    }

    private void setGroupParams(SolrQuery query) {
        query.set("group", true);
        query.set("group.ngroups", true);
        query.set("group.limit", 50);
        query.set("group.field", "productId");
        query.set("group.facet", true);
    }

    private void setFieldListParams(SolrQuery query, String country, String catalog) {
        String listPrice = "listPrice" + country;
        String salePrice =  "salePrice" + country;
        String dicountPercent = "discountPercent" + country;
        query.setFields("id", "productId", "title", "brand", "isToos", listPrice, salePrice, dicountPercent, "url" + country,
            "bayesianReviewAverage", "reviews", "isPastSeason", "freeGift" + catalog, "image");
        query.setParam(GroupCollapseParams.GROUP_COLLAPSE, true);
        query.setParam(GroupCollapseParams.GROUP_COLLAPSE_FL, listPrice + "," + salePrice + "," + dicountPercent);

    }

    private QueryResponse handleSpellCheck(SpellCheckResponse spellCheckResponse, T catalogSolrServer, SolrQuery query, String queryOp) throws SolrServerException{
        
        QueryResponse queryResponse = null;

        if(spellCheckResponse != null  && StringUtils.isNotBlank(spellCheckResponse.getCollatedResult())){
            //check if we have any spelling suggestion
            String tentativeCorrectedTerm = spellCheckResponse.getCollatedResult();

            //if we have spelling suggestions, try doing another search using 
            //q.op as the specified queryOp param (the default one is AND so we only add it if it's OR)
            //and use q="corrected phrase" to see if we can get results
            if("OR".equals(queryOp)){
                query.setParam("q.op", "OR");
                query.setParam("mm", getMinimumMatch());
            }
            query.setQuery(tentativeCorrectedTerm);
            queryResponse = catalogSolrServer.query(query);
            
            //if we didn't got any results from the search with q="corrected phrase" return null
            //otherwise return the results
            return isEmptySearch(queryResponse.getGroupResponse()) ? null : queryResponse;
            
        } else if("OR".equals(queryOp)) {
            //for the match any terms scenario with no corrected terms do another query
            query.setParam("q.op", "OR");
            query.setParam("mm", getMinimumMatch());
            queryResponse = catalogSolrServer.query(query);
            return isEmptySearch(queryResponse.getGroupResponse()) ? null : queryResponse;
        } else {
            //if we didn't got any corrected terms and are not in the match any term scenario, 
            //then return null
            return null;
        }
    }

    protected boolean isEmptySearch(GroupResponse groupResponse) {
        boolean noResults = true;            
        if(groupResponse != null) {
            for(GroupCommand command : groupResponse.getValues()){
                if(command.getNGroups() > 0){
                    noResults = false;
                    break;
                }
            }
        }
        return noResults;
    }

    @Override
    public UpdateResponse add(Collection<SolrInputDocument> docs) throws SearchServerException {
        return add(docs, getCatalogCollection(), Locale.ENGLISH);
    }

    @Override
    public UpdateResponse add(Collection<SolrInputDocument> docs, Locale locale) throws SearchServerException {
        return add(docs, getCatalogCollection(), locale);
    }

    public UpdateResponse add(Collection<SolrInputDocument> docs, String collection) throws SearchServerException {
        return add(docs, collection, Locale.ENGLISH);
    }

    public UpdateResponse add(Collection<SolrInputDocument> docs, String collection, Locale locale) throws SearchServerException {
        UpdateRequest req = new UpdateRequest();
        req.add(docs);
        req.setParam("collection", getCollectionName(collection, locale));

        try {
            return req.process(getSolrServer(collection, locale));
        } catch (SolrServerException ex) {
            throw create(UPDATE_EXCEPTION, ex);
        } catch (IOException ex) {
            throw create(UPDATE_EXCEPTION, ex);
        }
    }

    @Override
    public SolrPingResponse ping() throws SearchServerException {
        return ping(Locale.ENGLISH);
    }
    
    @Override
    public SolrPingResponse ping(Locale locale) throws SearchServerException {
        try {
            return getCatalogSolrServer(locale).ping();
        } catch (SolrServerException ex) {
            throw create(PING_EXCEPTION, ex);
        } catch (IOException ex) {
            throw create(PING_EXCEPTION, ex);
        }
    }

    @Override
    public UpdateResponse rollback() throws SearchServerException {
        return rollback(getCatalogCollection(), Locale.ENGLISH);
    }

    @Override
    public UpdateResponse rollback(Locale locale) throws SearchServerException {
        return rollback(getCatalogCollection(), locale);
    }

    public UpdateResponse rollback(String collection) throws SearchServerException {
        return rollback(collection, Locale.ENGLISH);
    }

    public UpdateResponse rollback(String collection, Locale locale) throws SearchServerException {
 
        try {
            return getSolrServer(collection, locale).rollback();
        } catch (SolrServerException ex) {
            throw create(COMMIT_EXCEPTION, ex);
        } catch (IOException ex) {
            throw create(COMMIT_EXCEPTION, ex);
        }

    }
    
    @Override
    public UpdateResponse commit() throws SearchServerException {
        return commit(getCatalogCollection(), Locale.ENGLISH);
    }

    @Override
    public UpdateResponse commit(Locale locale) throws SearchServerException {
        return commit(getCatalogCollection(), locale);
    }

    public UpdateResponse commit(String collection) throws SearchServerException {
        return commit(collection, Locale.ENGLISH);
    }

    public UpdateResponse commit(String collection, Locale locale) throws SearchServerException {
 
        try {
            return getSolrServer(collection, locale).commit();
        } catch (SolrServerException ex) {
            throw create(COMMIT_EXCEPTION, ex);
        } catch (IOException ex) {
            throw create(COMMIT_EXCEPTION, ex);
        }

    }

    @Override
    public UpdateResponse deleteByQuery(String query) throws SearchServerException {
        return deleteByQuery(query, getCatalogCollection());
    }

    @Override
    public UpdateResponse deleteByQuery(String query, Locale locale) throws SearchServerException {
        return deleteByQuery(query, getCatalogCollection(), locale);
    }

    public UpdateResponse deleteByQuery(String query, String collection) throws SearchServerException {
        return deleteByQuery(query, collection, Locale.ENGLISH);
    }

    public UpdateResponse deleteByQuery(String query, String collection, Locale locale) throws SearchServerException {
        UpdateRequest req = new UpdateRequest();
        req.deleteByQuery(query);
        req.setParam("collection", getCollectionName(collection, locale));

        try {
            return req.process(getSolrServer(collection, locale));
        } catch (SolrServerException ex) {
            throw create(UPDATE_EXCEPTION, ex);
        } catch (IOException ex) {
            throw create(UPDATE_EXCEPTION, ex);
        }
    }

    @Override
    public NamedList<Object> analyze(DocumentAnalysisRequest request) throws SearchServerException {
        return analyze(request, Locale.ENGLISH);
    }

    @Override
    public NamedList<Object> analyze(DocumentAnalysisRequest request, Locale locale) throws SearchServerException {
        try {
            return getCatalogSolrServer(locale).request(request);
        } catch (SolrServerException ex) {
            throw create(ANALYSIS_EXCEPTION, ex);
        } catch (IOException ex) {
            throw create(ANALYSIS_EXCEPTION, ex);
        }
    }

    @Override
    public NamedList<Object> analyze(FieldAnalysisRequest request) throws SearchServerException {
        return analyze(request, Locale.ENGLISH);
    }

    @Override
    public NamedList<Object> analyze(FieldAnalysisRequest request, Locale locale) throws SearchServerException {
        try {
            return getCatalogSolrServer(locale).request(request);
        } catch (SolrServerException ex) {
            throw create(ANALYSIS_EXCEPTION, ex);
        } catch (IOException ex) {
            throw create(ANALYSIS_EXCEPTION, ex);
        }
    }

    @Override
    public SearchResponse termVector(String query, String... fields) throws SearchServerException {
        return termVector(query, Locale.ENGLISH, fields);
    }

    @Override
    public SearchResponse termVector(String query, Locale locale, String... fields) throws SearchServerException {
        SolrQuery solrQuery = new SolrQuery(query);
        solrQuery.setRequestHandler("/tvrh");
        solrQuery.setFields(fields);
        solrQuery.setParam("tv.fl", "categoryName");

        try {
            QueryResponse queryResponse = getCatalogSolrServer(locale).query(solrQuery);
            return new SearchResponse(solrQuery, queryResponse, null, null, null, null, true);
        } catch (SolrServerException ex) {
            throw create(TERMS_EXCEPTION, ex);
        }
    }

    @Override
    public void onRepositoryItemChanged(String repositoryName, Set<String> itemDescriptorNames)
            throws RepositoryException, SearchServerException {
        if (repositoryName.endsWith(getSearchRepository().getRepositoryName())) {
            if (itemDescriptorNames.contains(SearchRepositoryItemDescriptor.SYNONYM)
                    || itemDescriptorNames.contains(SearchRepositoryItemDescriptor.SYNONYM_LIST)) {
                // TODO: support locaclized synonyms
                exportSynonyms(Locale.ENGLISH);
                reloadCollections();
            }
            if (itemDescriptorNames.contains(SearchRepositoryItemDescriptor.RULE)
                    || itemDescriptorNames.contains(SearchRepositoryItemDescriptor.BOOST_RULE)
                    || itemDescriptorNames.contains(SearchRepositoryItemDescriptor.BLOCK_RULE)
                    || itemDescriptorNames.contains(SearchRepositoryItemDescriptor.FACET_RULE)
                    || itemDescriptorNames.contains(SearchRepositoryItemDescriptor.RANKING_RULE)
                    || itemDescriptorNames.contains(SearchRepositoryItemDescriptor.REDIRECT_RULE)) {
                indexRules();
            }
        }
    }

    @Override
    public void onProductChanged(RepositoryItem product) throws RepositoryException, SearchServerException {
        throw new UnsupportedOperationException();
    }

    public void exportSynonyms() throws RepositoryException, SearchServerException {
        exportSynonyms(Locale.ENGLISH);
    }

    /**
     * Export the synonym lists in the search repository to Zoo Keeper. Each
     * synonym list is exported into its own file. When renaming a new list or
     * creating its synonyms won't have effect until its get configured in an
     * analyzer.
     *
     * When renaming a list that is currently being use by an analyzer it won't
     * be deleted to prevent the analyzer from breaking. However, new changes to
     * the renamed list won't take effect.
     *
     * @throws RepositoryException
     *             when an error occurs while retrieving synonyms from the
     *             repository
     * @throws ExportSynonymException
     *             if an error occurs while exporting the synonym list
     */
    public void exportSynonyms(Locale locale) throws RepositoryException, SearchServerException {
        RepositoryView view = searchRepository.getView(SearchRepositoryItemDescriptor.SYNONYM_LIST);
        RepositoryItem[] synonymLists = getSynonymListRql().executeQuery(view, null);
        if (synonymLists != null) {
            for (RepositoryItem synonymList : synonymLists) {
                exportSynonymList(synonymList, locale);
            }
        } else {
            if (isLoggingInfo()) {
                logInfo("No synomym lists were exported to ZooKeeper");
            }
        }
    }

    protected abstract void exportSynonymList(RepositoryItem synonymList, Locale locale) throws RepositoryException, SearchServerException;

    /**
     * Reloads the catalog and rule collections
     *
     * @throws SearchServerException if an error occurs while reloading the core
     */
    public void reloadCollections() throws SearchServerException {
        // @TODO add support to reload all locale cores
        String collectionName = getCatalogCollection(Locale.ENGLISH);
        reloadCollection(collectionName, Locale.ENGLISH);
        collectionName = getRulesCollection(Locale.ENGLISH);
        reloadCollection(collectionName, Locale.ENGLISH);
    }

    /**
     * Reloads the core
     *
     * @param collectionName
     *            the cored to be reloaded
     *
     * @throws SearchServerException if an error occurs while reloading the core
     * 
     */
     public abstract void reloadCollection(String collectionName, Locale locale) throws SearchServerException;

    /**
     * Indexes all repository rules in the search index
     *
     * @throws RepositoryException
     *             is an exception occurs while retrieving data from the
     *             repository
     * @throws SolrServerException
     *             if an exception occurs while indexing the document
     */
    public void indexRules() throws RepositoryException, SearchServerException {
        long startTime = System.currentTimeMillis();
        RepositoryView view = getSearchRepository().getView(SearchRepositoryItemDescriptor.RULE);
        int ruleCount = ruleCountRql.executeCountQuery(view, null);

        if (ruleCount == 0) {
            deleteByQuery("*:*", getRulesCollection());
            commit(getRulesCollection());

            if (isLoggingInfo()) {
                logInfo("No rules found for indexing");
            }
            return;
        }

        if (isLoggingInfo()) {
            logInfo("Started rule feed for " + ruleCount + " rules");
        }
        
        int processed = 0;
                
        try {
            //TODO fix this
            deleteByQuery("*:*", getRulesCollection());
    
            List<SolrInputDocument> docs = new ArrayList<SolrInputDocument>();
            Integer[] rqlArgs = new Integer[] { 0, getRuleBatchSize() };
            RepositoryItem[] rules = ruleRql.executeQueryUncached(view, rqlArgs);
        
            // TODO fix: add support for localized rules
            RuleManager ruleManager = new RuleManager(getSearchRepository(), getRulesBuilder(), getRulesSolrServer(Locale.ENGLISH));
            while (rules != null) {
    
                for (RepositoryItem rule : rules) {
                    docs.add(ruleManager.createRuleDocument(rule));
                    ++processed;
                }
                add(docs, getRulesCollection());
    
                rqlArgs[0] += getRuleBatchSize();
                rules = ruleRql.executeQueryUncached(view, rqlArgs);
    
                if (isLoggingInfo()) {
                    logInfo("Processed " + processed + " out of " + ruleCount);
                }
            }
            commit(getRulesCollection());            
        } catch (Exception e) {
            rollback(getRulesCollection());
            if(isLoggingError()) {
                logError("Error exporting indexing rules", e);
            }
        }

        if (isLoggingInfo()) {
            logInfo("Rules feed finished in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds, "
                    + processed + " rules were indexed");
        }
    }
    
    
    private List<CategoryGraph> createCategoryGraph(SearchResponse searchResponse, String path, String catalogId,
            String categoryId) {

        List<CategoryGraph> categoryGraphList = new ArrayList<CategoryGraph>();

        for (Facet facet : searchResponse.getFacets()) {
            if (CATEGORY_PATH.equalsIgnoreCase(facet.getName())) {
                searchResponse.removeFacet(facet.getName());
                return createCategoryGraphAux(facet, path, catalogId, categoryId);
            }
        }
        return categoryGraphList;
    }

    private List<CategoryGraph> createCategoryGraphAux(Facet facet, String path, String catalogId, String categoryId) {
        List<CategoryGraph> categoryGraphList = new ArrayList<CategoryGraph>();
        if (facet != null) {

            CategoryGraphBuilder categoryFacetBuilder = new CategoryGraphBuilder();

            // iterate through the flat category facet structure and create a
            // graph from it
            for (Filter filter : facet.getFilters()) {
                if (isLoggingDebug()) {
                    String filterPath = Utils.findFilterExpressionByName(filter.getPath(), CATEGORY_PATH);
                    logDebug("Generating CategoryGraph for path: " + filterPath);
                }
                categoryFacetBuilder.addPath(filter);
            }

            if (categoryId == null) {
                // no category filtering scenario. Return top level list
                categoryGraphList = categoryFacetBuilder.getCategoryGraphList();
            } else {
                // category filtering scenario. Search hierarchy for the actual
                // result node.
                CategoryGraph currentLevelVO = categoryFacetBuilder.search(categoryId, categoryFacetBuilder.getParentNode());
                if (currentLevelVO != null) {
                    categoryGraphList = currentLevelVO.getCategoryGraphNodes();
                } else {
                    if (isLoggingDebug()) {
                        logDebug("The CategoryGraph is empty for catalog: "
                                + catalogId
                                + " and category: "
                                + categoryId
                                + " path: " + path
                                + " This is expected for leaf categories");
                    }
                }
            }
        }

        return categoryGraphList;
    }
}

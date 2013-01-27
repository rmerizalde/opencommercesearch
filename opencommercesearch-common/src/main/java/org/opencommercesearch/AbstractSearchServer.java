package org.opencommercesearch;

import atg.multisite.Site;
import atg.multisite.SiteContextManager;
import atg.nucleus.GenericService;
import atg.repository.Repository;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryView;
import atg.repository.rql.RqlStatement;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.DocumentAnalysisRequest;
import org.apache.solr.client.solrj.request.FieldAnalysisRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
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
    private Repository searchRepository;
    private RqlStatement synonymRql;
    private RqlStatement ruleCountRql;
    private RqlStatement ruleRql;
    private int ruleBatchSize;

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

    public void setRulesCollection(String ruleCollection) {
        this.rulesCollection = ruleCollection;
    }

    public Repository getSearchRepository() {
        return searchRepository;
    }

    public void setSearchRepository(Repository searchRepository) {
        this.searchRepository = searchRepository;
    }

    public RqlStatement getSynonymRql() {
        return synonymRql;
    }

    public void setSynonymRql(RqlStatement synonymRql) {
        this.synonymRql = synonymRql;
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

    public int getRuleBatchSize() {
        return ruleBatchSize;
    }

    public void setRuleBatchSize(int ruleBatchSize) {
        this.ruleBatchSize = ruleBatchSize;
    }

    @Override
    public SearchResponse search(SolrQuery query, FilterQuery... filterQueries) throws SearchServerException {
        return search(query, SiteContextManager.getCurrentSite(), Locale.ENGLISH, filterQueries);
    }

    @Override
    public SearchResponse search(SolrQuery query, Locale locale, FilterQuery... filterQueries) throws SearchServerException {
        return search(query, SiteContextManager.getCurrentSite(), locale, filterQueries);
    }

    @Override
    public SearchResponse search(SolrQuery query, Site site, FilterQuery... filterQueries) throws SearchServerException {
        return search(query, site, Locale.ENGLISH, filterQueries);
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
        if (site == null) {
            throw new IllegalArgumentException("Missing site");
        }
        if (catalog == null) {
            throw new IllegalArgumentException("Missing catalog");
        }
        long startTime = System.currentTimeMillis();
        
        query.addFacetField("category");
        query.set("f.category.facet.mincount", 1);

        query.set("group", true);
        query.set("group.ngroups", true);
        query.set("group.limit", 50);
        query.set("group.field", "productId");
        query.set("group.facet", true);


        RuleManager ruleManager = new RuleManager(getSearchRepository(), getRulesSolrServer(locale));
        try {
            ruleManager.setRuleParams(filterQueries, catalog, query);
            
            if(ruleManager.getRules().containsKey(SearchRepositoryItemDescriptor.REDIRECT_RULE)){
            	Map<String, List<RepositoryItem>> rules = ruleManager.getRules();
            	List<RepositoryItem> redirects = rules.get(SearchRepositoryItemDescriptor.REDIRECT_RULE);
            	if(redirects != null){
            		RepositoryItem redirect = redirects.get(0);
            		return new SearchResponse(null, null, null, (String) redirect.getPropertyValue(RedirectRuleProperty.URL));
            	}
            }
            
        } catch (RepositoryException ex) {
            if (isLoggingError()) {
                logError("Unable to load search rules", ex);
            }
        } catch (SolrServerException ex) {
            if (isLoggingError()) {
                logError("Unable to load search rules", ex);
            }
        } finally {
            if (query.getSortFields() == null || query.getSortFields().length == 0) {
                query.addSortField("isToos", SolrQuery.ORDER.asc);
                query.addSortField("score", SolrQuery.ORDER.desc);
            }
        }

        try {
            QueryResponse queryResponse = getCatalogSolrServer(locale).query(query);

            long searchTime = System.currentTimeMillis() - startTime;
         // @TODO change ths to debug mode
            if (isLoggingInfo()) {
                logInfo("Search time is " + searchTime + ", search engine time is " + queryResponse.getQTime());
            }
            return new SearchResponse(queryResponse, ruleManager, filterQueries, null);
        } catch (SolrServerException ex) {
            throw create(SEARCH_EXCEPTION, ex);
        }
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
        req.setParam("collection", getCatalogCollection(locale));

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
        req.setParam("collection", getCatalogCollection(locale));

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
            return new SearchResponse(queryResponse, null, null, null);
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
                    || itemDescriptorNames.contains(SearchRepositoryItemDescriptor.REDIRECT_RULE)) {
                indexRules();
            }
        }
    }

    @Override
    public void onProductChanged(RepositoryItem product) throws RepositoryException, SearchServerException {
        throw new UnsupportedOperationException();
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
        RepositoryItem[] synonymLists = getSynonymRql().executeQuery(view, null);
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

    protected abstract void exportSynonymList(RepositoryItem synonymList, Locale locale) throws SearchServerException;

    /**
     * Reloads the catalog and rule collections
     *
     * @throws SearchServerException if an error occurs while reloading the core
     */
    public void reloadCollections() throws SearchServerException {
        // @TODO add support to reload all locale cores
        String collectionName = getCatalogCollection();
        reloadCollection(collectionName, Locale.ENGLISH);
        collectionName = getRulesCollection();
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

        // TODO fix this
        deleteByQuery("*:*", getRulesCollection());

        List<SolrInputDocument> docs = new ArrayList<SolrInputDocument>();
        Integer[] rqlArgs = new Integer[] { 0, getRuleBatchSize() };
        RepositoryItem[] rules = ruleRql.executeQueryUncached(view, rqlArgs);

        int processed = 0;

        // TODO fix: add support for localized rules
        RuleManager ruleManager = new RuleManager(getSearchRepository(), getRulesSolrServer(Locale.ENGLISH));
        while (rules != null) {

            for (RepositoryItem rule : rules) {
                docs.add(ruleManager.createRuleDocument(rule));
                ++processed;
            }
            add(docs, getRulesCollection());
            commit(getRulesCollection());

            rqlArgs[0] += getRuleBatchSize();
            rules = ruleRql.executeQueryUncached(view, rqlArgs);

            if (isLoggingInfo()) {
                logInfo("Processed " + processed + " out of " + ruleCount);
            }
        }

        if (isLoggingInfo()) {
            logInfo("Rules feed finished in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds, "
                    + processed + " rules were indexed");
        }
    }
}

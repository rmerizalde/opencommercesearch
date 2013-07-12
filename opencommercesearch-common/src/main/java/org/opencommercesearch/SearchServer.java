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

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.request.DocumentAnalysisRequest;
import org.apache.solr.client.solrj.request.FieldAnalysisRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;

import atg.multisite.Site;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import org.apache.solr.common.util.NamedList;

/**
 * This interface represents a facade for a Solr server. Currently, it exposes
 * limited functionality but will be extended.
 *
 * @author rmerizalde
 *
 */
public interface SearchServer {

    void connect() throws IOException;
    void close() throws IOException;

    SearchResponse browse(BrowseOptions options, SolrQuery query, FilterQuery... filterQueries) throws SearchServerException;
    SearchResponse browse(BrowseOptions options, SolrQuery query, Locale locale, FilterQuery... filterQueries) throws SearchServerException;
    
    SearchResponse browse(BrowseOptions options, SolrQuery query, Site site, FilterQuery... filterQueries) throws SearchServerException;
    SearchResponse browse(BrowseOptions options, SolrQuery query, Site site, Locale locale, FilterQuery... filterQueries) throws SearchServerException;
    
    SearchResponse search(SolrQuery query, FilterQuery... filterQueries) throws SearchServerException;
    SearchResponse search(SolrQuery query, Locale locale, FilterQuery... filterQueries) throws SearchServerException;

    SearchResponse search(SolrQuery query, Site site, FilterQuery... filterQueries) throws SearchServerException;
    SearchResponse search(SolrQuery query, Site site, Locale locale, FilterQuery... filterQueries) throws SearchServerException;

    SearchResponse search(SolrQuery query, Site site, RepositoryItem catalog, FilterQuery... filterQueries)
            throws SearchServerException;
    SearchResponse search(SolrQuery query, Site site, RepositoryItem catalog, Locale locale, FilterQuery... filterQueries)
             throws SearchServerException;

    Facet getFacet(Site site, Locale locale, String fieldFacet, int facetLimit, FilterQuery... filterQueries) throws SearchServerException;
    
    QueryResponse query(SolrQuery solrQuery,  String collection, Locale locale) throws SearchServerException;
    
    UpdateResponse add(Collection<SolrInputDocument> docs) throws SearchServerException;
    UpdateResponse add(Collection<SolrInputDocument> docs, Locale locale) throws SearchServerException;
    UpdateResponse add(Collection<SolrInputDocument> docs, String collection, Locale locale) throws SearchServerException;
    
    UpdateResponse rollback() throws SearchServerException;
    UpdateResponse rollback(Locale locale) throws SearchServerException;
    UpdateResponse rollback(String collection, Locale locale) throws SearchServerException;
    
    UpdateResponse commit() throws SearchServerException;
    UpdateResponse commit(Locale locale) throws SearchServerException;
    UpdateResponse commit(String collection, Locale locale) throws SearchServerException;

    UpdateResponse deleteByQuery(String query) throws SearchServerException;
    UpdateResponse deleteByQuery(String query, Locale locale) throws SearchServerException;
    UpdateResponse deleteByQuery(String query, String collection, Locale locale) throws SearchServerException;
    
    SolrPingResponse ping() throws SearchServerException;
    SolrPingResponse ping(Locale locale) throws SearchServerException;

    NamedList<Object> analyze(DocumentAnalysisRequest request) throws SearchServerException;
    NamedList<Object> analyze(DocumentAnalysisRequest request, Locale locale) throws SearchServerException;
    
    /**
     * Performs an analysis of a field type or field name for a given value.
     *
     * @param request the analysis request
     * @return returns the anlyser results for the give field
     * @throws SearchServerException if an exception occurs while analyzing the field
     */
    NamedList<Object> analyze(FieldAnalysisRequest request) throws SearchServerException;
    NamedList<Object> analyze(FieldAnalysisRequest request, Locale locale) throws SearchServerException;
    NamedList<Object> analyze(FieldAnalysisRequest request, String collection, Locale locale) throws SearchServerException;
    
    /**
     * Returns the indexed terms for the given list of fields for each document that matches the given query.
     *
     * If the term vector is not enabled, or positions and offsets are set to false the response will contain warnings
     * per field.
     *
     * @param query the query to search
     * @param fields the fields to retrieve terms for
     * @return the search response with the terms.
     * @throws SearchServerException if an exception occurs while retrieving the terms
     */
    SearchResponse termVector(String query, String... fields) throws SearchServerException;
    SearchResponse termVector(String query, Locale locale, String... fields) throws SearchServerException;


    /**
     * This method can be used to notify the SearchServer that an item
     * descriptor was modified in the search repository. The server can analyze
     * the modification and take proper actions (export configuration, index
     * products, etc.)
     * 
     * 
     * @param repositoryName
     *            is the name of the repository where one or more items
     *            descriptor changed
     * @param itemDescriptorNames
     *            is a list of item descriptor names that were modified
     * @throws RepositoryException
     *             if an error occurs when accessing the repository
     * @throws SearchServerException
     *             if an exception occurs while taking some action. Most likely,
     *             this will be wrapping another exception depending of the
     *             implementation
     * 
     */
    void onRepositoryItemChanged(String repositoryName, Set<String> itemDescriptorNames) throws RepositoryException,
            SearchServerException;

    /**
     * TBD
     * 
     * @param product
     * @throws RepositoryException
     * @throws SearchServerException
     */
    void onProductChanged(RepositoryItem product) throws RepositoryException, SearchServerException;
}

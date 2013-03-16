package org.opencommercesearch.service;

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

import atg.adapter.gsa.GSARepository;
import atg.nucleus.GenericService;
import atg.nucleus.ServiceException;
import atg.repository.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * This class implements an abstract service to load data that is process sequentially from an SQL data source. The
 * service will cache chunks of record in memory to minimize I/O operations. Accessing the data randomly may cause the
 * loader to reload data again back and forth. The data consumer should process the data in the same order the is loaded
 * I/O operations.
 *
 * The concrete class must specify the SQL query that the loader will use to retrieve data. The SQL statement must support
 * three parameters in the following order:
 *
 * 1. id: a Comparable object representing the unique id of the record
 * 2. offset: the offset use to load the data in batches
 * 3. batch size: the amount of records that will be cached in memory.
 *
 * In addition, the SQL must return a column named id for each row.
 *
 * This service keep tracks of the minimum id and maximum id in the cache. If the consumer requests the record for an id
 * within the range (inclusive) and there's no such record it will return null and the request will be counted as cache
 * hit.
 *
 * The loader access the data source if the id is less that the minimum than the greater id. Each access is counted as
 * cache miss. However, if the requested id is higher than the maximum id but there are no records left in the data source
 * it won't access the data source and the request is counted as a cache hit.
 *
 * This class is not thread safe.
 *
 * @rmerizalde
 */
public abstract class SequentialDataLoaderService<K extends Comparable, V> extends GenericService {

    private Repository repository;
    private K minId;
    private K maxId;
    private Map<K, V> cache;
    private long requestCount;
    private long cacheHitCount;
    private int cacheSize = 1000;
    private String sqlQuery;

    public Repository getRepository() {
        return repository;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public long getRequestCount() {
        return requestCount;
    }

    public long getCacheHitCount() {
        return cacheHitCount;
    }

    public int getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }

    public String getSqlQuery() {
        return sqlQuery;
    }

    public void setSqlQuery(String sqlQuery) {
        this.sqlQuery = sqlQuery;
    }

    public double getCacheHitRatio() {
        synchronized (this) {
            if (requestCount == 0) {
                return 0d;
            }
            return (cacheHitCount / (double) requestCount) * 100;
        }
    }

    @Override
    public void doStartService() throws ServiceException {
        super.doStartService();

        if (getSqlQuery() == null) {
            throw new ServiceException("SQL query is required");
        }

        if (getRepository() == null) {
            throw new ServiceException("Repository is required");
        }

        synchronized (this) {
            requestCount = 0;
            cacheHitCount = 0;
        }
    }

    /**
     * Return the item mapped to the given id.
     * @param id the key to search for
     * @return the item mapped to the given id
     */
    public V getItem(K id) {
        requestCount++;

        if (minId != null && minId.compareTo(maxId) == 0 && id.compareTo(maxId) > 0) {
            cacheHitCount++;
            return null;
        }

        if (maxId == null || id.compareTo(minId) < 0 || id.compareTo(maxId) > 0) {
            loadData(id);
        } else {
            cacheHitCount++;
        }

        return cache.get(id);
    }

    /**
     * Helper method to load the data for the given id and another chuck of items after it.
     */
    private void loadData(K id) {
        Connection connection = null;

        try {
            connection = ((GSARepository) getRepository()).getDataSource().getConnection();
            PreparedStatement stmt = null;
            try {
                stmt = connection.prepareStatement(getSqlQuery());
                loadData(stmt, id);
            } catch (SQLException ex) {
                if (isLoggingError()) {
                    logError("Could not load inventory into memory", ex);
                }
            } finally {
                try {
                    if (null != stmt) {
                        stmt.close();
                    }
                } catch (SQLException ex) {
                    if (isLoggingError()) {
                        logError("Could not close prepared statements ", ex);
                    }
                }
            }
        } catch (SQLException ex) {
            if (isLoggingError()) {
                logError("Could not connect to the database", ex);
            }
        } finally {
            try {
                if (null != connection) {
                    connection.close();
                }
            } catch (SQLException ex) {
                if (isLoggingError()) {
                    logError("Could not close database connection ", ex);
                }
            }
        }
    }

    /**
     * Another helper method that actually does executes the query
     */
    private void loadData(PreparedStatement stmt, K id) throws SQLException {
        if (cache == null) {
            cache = new HashMap<K, V>(getCacheSize());
        } else {
            cache.clear();
        }

        long startTime = System.currentTimeMillis();
        if (isLoggingDebug()) {
            logDebug("Loading record with id " + id + " + " + getCacheSize() + " successors");
        }

        int offset = 1;
        int hits = getCacheSize();

        stmt.setObject(1, id);
        stmt.setInt(2, offset);
        stmt.setInt(3, hits);
        minId = id;
        maxId = id;

        if (stmt.execute()) {
            ResultSet rs = stmt.getResultSet();

            while (rs.next()) {
                K nextId = (K) rs.getObject("id");
                V item = processRecord(rs);
                if (item != null) {
                    cache.put(nextId, item);
                }
                maxId = nextId;
            }

            try {
                rs.close();
            } catch (SQLException ex) {
                if (isLoggingError()) {
                    logError("Error result set", ex);
                }
            }
        }

        if (isLoggingInfo()) {
            logInfo("Loaded data in " + ((System.currentTimeMillis() - startTime) / 1000)
                    + " seconds. Id range: " + minId + " - " + maxId + ". Cache contains "
                    + cache.size() + " items");
        }
    }

    /**
     * Process each row in the result set. Concrete classes must override this method to build the item the will be
     * stored in the cache
     * @param rs is the result set
     * @return the item that will be cache for the current row in the result set
     * @throws SQLException is exception occurrs while retrieving the row columns
     */
    protected abstract V processRecord(ResultSet rs) throws SQLException;


}

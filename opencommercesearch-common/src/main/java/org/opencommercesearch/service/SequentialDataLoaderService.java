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
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class implements an abstract service to load data that is process sequentially from an SQL data source. The
 * service will cache chunks of record in memory to minimize I/O operations. Accessing the data randomly may cause the
 * loader to reload data again back and forth. The data consumer should process the data in the same order the is loaded
 * I/O operations.
 *
 * The concrete class must specify the SQL query that the loader will use to retrieve data. The SQL statement must support
 * three parameters:
 *
 * 1. id: a Comparable object representing the unique id of the record
 * 2. offset: the offset use to load the data in batches
 * 3. row count: the amount of rows that will be cached in memory.
 *
 * Concrete classes can override the method preparedArguments if additional parameters are required.
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
 * The cache implementation is delegated to subclasses. Subclasses decide how cache data is stored in memory. For instance,
 * the cache could be backed up by concurrent hash map or a regular map using a ThreadLocal object.
 *
 * @author rmerizalde
 */
public abstract class SequentialDataLoaderService<K extends Comparable, V> extends GenericService {

    public interface  RecordProcessor<V> {
        /**
         * Process each row in a result set. Concrete classes must provide an implementation of this interface in order
         * to build the item the will be stored in a cache
         * @param rs is the result set
         * @return the item that will be cache for the current row in the result set
         * @throws SQLException is exception occurs while retrieving the row columns
         */
        V processRecord(ResultSet rs) throws SQLException;
    }

    private Repository repository;
    private AtomicLong requestCount = new AtomicLong(0);
    private AtomicLong cacheHitCount = new AtomicLong(0);
    private int maxCacheSize = 1000;
    private String sqlQuery;
    private RecordProcessor<V> recordProcessor;

    /**
     * Time in milliseconds when the current in memory data chunk to expire (and force a reload from the database).
     */
    private AtomicLong expireTime = new AtomicLong();

    /**
     * Time in milliseconds when the current in memory data will expire.
     */
    private AtomicLong currentExpireTime = new AtomicLong(Long.MAX_VALUE);

    /**
     * Whether or not the current data chunk in memory will expire at some point.
     */
    private boolean enableExpiration = false;

    public Repository getRepository() {
        return repository;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public long getRequestCount() {
        return requestCount.get();
    }

    public long getCacheHitCount() {
        return cacheHitCount.get();
    }

    public int getMaxCacheSize() {
        return maxCacheSize;
    }

    public void setMaxCacheSize(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
    }

    public String getSqlQuery() {
        return sqlQuery;
    }

    public void setSqlQuery(String sqlQuery) {
        this.sqlQuery = sqlQuery;
    }

    public double getCacheHitRatio() {
        synchronized (this) {
            if (requestCount.get() == 0) {
                return 0d;
            }
            return (cacheHitCount.get() / (double) requestCount.get()) * 100;
        }
    }

    protected abstract K getMinId();

    protected abstract void setMinId(K minId);

    protected abstract K getMaxId();

    protected abstract void setMaxId(K maxId);

    protected abstract V putCachedItem(K id, V value);

    protected abstract V getCachedItem(K id);

    protected abstract void clearCache();

    protected abstract int cacheSize();

    public RecordProcessor<V> getRecordProcessor() {
        return recordProcessor;
    }

    protected void setRecordProcessor(RecordProcessor<V> recordProcessor) {
        this.recordProcessor = recordProcessor;
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
            requestCount.set(0);
            cacheHitCount.set(0);
        }
    }

    /**
     * Return the item mapped to the given id.
     * @param id the key to search for
     * @return the item mapped to the given id
     */
    @SuppressWarnings("unchecked")
    public V getItem(K id) {
        requestCount.incrementAndGet();

        if(enableExpiration && currentExpireTime.get() <= System.currentTimeMillis()) {
            if(isLoggingDebug()) {
                logDebug("Cache expired, forcing data load");
            }

            setMinId(null);
            setMaxId(null);
        }

        K min = getMinId();
        K max = getMaxId();

        if (min != null && min.compareTo(max) == 0 && id.compareTo(max) > 0) {
            cacheHitCount.incrementAndGet();
            return null;
        }

        if (max == null || id.compareTo(min) < 0 || id.compareTo(max) > 0) {
            loadData(id);
        } else {
            cacheHitCount.incrementAndGet();
        }


        return getCachedItem(id);
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
                loadData(id, stmt);
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
                        logError("Could not close prepared statement ", ex);
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
    @SuppressWarnings("unchecked")
    private void loadData(K id, PreparedStatement stmt) throws SQLException {
        clearCache();

        long startTime = System.currentTimeMillis();
        if (isLoggingDebug()) {
            logDebug(Thread.currentThread() + " - Loading record with id " + id + " + " + getMaxCacheSize() + " successors");
        }

        int offset = 1;
        int rowCount = getMaxCacheSize();

        prepareArguments(id, offset, rowCount, stmt);
        setMinId(id);
        setMaxId(id);
        currentExpireTime.set(System.currentTimeMillis() + expireTime.get());

        if (stmt.execute()) {
            ResultSet rs = stmt.getResultSet();

            while (rs.next()) {
                K nextId = (K) rs.getObject("id");
                V item = getRecordProcessor().processRecord(rs);
                if (item != null) {
                    putCachedItem(nextId, item);
                }
                setMaxId(nextId);
            }

            try {
                rs.close();
            } catch (SQLException ex) {
                if (isLoggingError()) {
                    logError("Exception closing result set", ex);
                }
            }
        }

        if (isLoggingInfo()) {
            logInfo(Thread.currentThread() + " - Loaded data in " + ((System.currentTimeMillis() - startTime) / 1000)
                    + " seconds. Id range: " + getMinId() + " - " + getMaxId() + ". Cache contains "
                    + cacheSize() + " items");
        }
    }

    /**
     * Utility method to execute SQL statements
     *
     * @param sqlQuery the SQL to be executed
     * @param recordProcessor the processor to process each record
     */
    protected <T> void loadData(String sqlQuery, RecordProcessor<T> recordProcessor, Object... args) {
        Connection connection = null;

        try {
            connection = ((GSARepository) getRepository()).getDataSource().getConnection();
            PreparedStatement stmt = null;
            try {
                stmt = connection.prepareStatement(sqlQuery);

                int argCount = 1;
                for (Object arg : args) {
                    stmt.setObject(argCount++, arg);
                }

                if (stmt.execute()) {
                    ResultSet rs = stmt.getResultSet();

                    while (rs.next()) {
                        recordProcessor.processRecord(rs);
                    }
                    try {
                        rs.close();
                    } catch (SQLException ex) {
                        if (isLoggingError()) {
                            logError("Exception closing result set", ex);
                        }
                    }
                }
            } catch (SQLException ex) {
                if (isLoggingError()) {
                    logError("Could execute query '" + sqlQuery +"'", ex);
                }
            } finally {
                try {
                    if (null != stmt) {
                        stmt.close();
                    }
                } catch (SQLException ex) {
                    if (isLoggingError()) {
                        logError("Could not close prepared statement ", ex);
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
     * Sets the arguments for the prepared statements. Concrete classes can override this method if different parameters
     * are needed.
     *
     * @param id the id of the record to start loading from
     * @param offset is the offset of the batch or row been loaded.
     * @param rowCount the number of rows to load
     * @param stmt the statement
     * @throws SQLException if an error occurs
     */
    protected void prepareArguments(K id, int offset, int rowCount, PreparedStatement stmt) throws SQLException {
        stmt.setObject(1, id);
        stmt.setInt(2, offset);
        stmt.setInt(3, rowCount);
    }

    public long getExpireTime() {
        return expireTime.get();
    }

    public void setExpireTime(long expireTime) {
        this.expireTime.set(expireTime);
    }

    public long getCurrentExpireTime() {
        return currentExpireTime.get();
    }

    public void setCurrentExpireTime(long currentExpireTime) {
        this.currentExpireTime.set(currentExpireTime);
    }

    public boolean isEnableExpiration() {
        return enableExpiration;
    }

    public void setEnableExpiration(boolean enableExpiration) {
        this.enableExpiration = enableExpiration;
    }
}

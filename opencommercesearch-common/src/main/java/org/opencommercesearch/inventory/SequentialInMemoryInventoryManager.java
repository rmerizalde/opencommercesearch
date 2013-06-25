package org.opencommercesearch.inventory;

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

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import org.opencommercesearch.service.SequentialDataLoaderService;

import atg.commerce.inventory.InventoryException;
import atg.commerce.inventory.InventoryManager;
import atg.nucleus.ServiceException;

/**
 * This class implements a sequential in-memory inventory manager to help speeding up the feed
 * process. This inventory manager doesn't implement all the methods provided by the
 * interface, but only the methods required by search feed. This functionality can
 * be customized by tweaking the SQL query properties.
 *
 * The inventory manager assumes the sku ids are queried in order. When a sku with an id greater
 * to the last sku id in the cache is accessed the component will discard all the items in cache and load
 * the requested skus and n successor skus.
 *
 * The manager also allows going back and forth. However, going back in the list will require more
 * repository requests.
 *
 * The class is not thread safe.
 *
 * @TODO currently loads stock level, make it customizable so subclasses can load other inventory properties (e.g. status)
 */
public class SequentialInMemoryInventoryManager extends SequentialDataLoaderService<String, InventoryEntry> implements InventoryManager {

    public static final String LOCALE_SEPARATOR = ":";

    private String inventoryName = "In Memory Inventory";

    @Override
    public void doStartService() throws ServiceException {
        super.doStartService();
        setRecordProcessor(new RecordProcessor<InventoryEntry>() {
            @Override
            public InventoryEntry processRecord(ResultSet rs) throws SQLException {
                InventoryEntry entry = new InventoryEntry();
                entry.setStockLevel(rs.getLong("stock_level"));
                entry.setBackorderLevel(rs.getLong("backorder_level"));
                entry.setAvailabilityStatus(rs.getInt("avail_status"));
                return entry;
            }
        });
    }

    @Override
    public void acquireInventoryLocks(List itemIds) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int backorder(String id, long howMany) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int decreaseBackorderLevel(String id, long number) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int decreasePreorderLevel(String id, long number) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int decreaseStockLevel(String id, long number) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getInventoryName() {
        return inventoryName;
    }

    public void setInventoryName(String inventoryName) {
        this.inventoryName = inventoryName;
    }

    @Override
    public int increaseBackorderLevel(String id, long number) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int increasePreorderLevel(String id, long number) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int increaseStockLevel(String id, long number) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int inventoryWasUpdated(List id) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int preorder(String id, long howMany) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int purchase(String id, long howMany) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int purchaseOffBackorder(String id, long howMany) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int purchaseOffPreorder(String id, long howMany) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Date queryAvailabilityDate(String id) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int queryAvailabilityStatus(String id) throws InventoryException {
        id = getId(id);

        InventoryEntry entry = getItem(id);

        if (entry == null) {
            throw new InventoryException("Inventory not found for " + id);
        }

        if (isLoggingDebug()) {
            logDebug("Availability Status for " + id + " is " + entry.getAvailabilityStatus());
        }

        return entry.getAvailabilityStatus();
    }

    @Override
    public long queryBackorderLevel(String id) throws InventoryException {
        id = getId(id);

        InventoryEntry entry = getItem(id);

        if (entry == null) {
            throw new InventoryException("Inventory not found for " + id);
        }

        if (isLoggingDebug()) {
            logDebug("Backorder level for " + id + " is " + entry.getBackorderLevel());
        }

        return entry.getBackorderLevel();
    }

    @Override
    public long queryBackorderThreshold(String id) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long queryPreorderLevel(String id) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long queryPreorderThreshold(String id) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long queryStockLevel(String id) throws InventoryException {
        id = getId(id);

        InventoryEntry entry = getItem(id);

        if (entry == null) {
            throw new InventoryException("Inventory not found for " + id);
        }

        if (isLoggingDebug()) {
            logDebug("Stock level for " + id + " is " + entry.getStockLevel());
        }

        return entry.getStockLevel();
    }

    @Override
    public long queryStockThreshold(String id) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void releaseInventoryLocks(List itemIds) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int setAvailabilityDate(String id, Date date) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int setAvailabilityStatus(String id, int status) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int setBackorderLevel(String id, long number) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int setBackorderThreshold(String id, long number) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int setPreorderLevel(String id, long number) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int setPreorderThreshold(String id, long number) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int setStockLevel(String id, long number) throws InventoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int setStockThreshold(String id, long number) throws InventoryException {
        throw new UnsupportedOperationException();
    }
    
    private String getId(String id) {
        int localeSeparatorIndex = id.lastIndexOf(LOCALE_SEPARATOR);
        if (localeSeparatorIndex != -1) {
            id = id.substring(0, localeSeparatorIndex);
        }
        return id;
    }
}

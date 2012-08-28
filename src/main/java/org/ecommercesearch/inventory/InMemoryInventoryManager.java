package org.ecommercesearch.inventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import atg.adapter.gsa.GSARepository;
import atg.commerce.inventory.InventoryException;
import atg.commerce.inventory.InventoryManager;
import atg.nucleus.GenericService;
import atg.nucleus.ServiceException;
import atg.repository.Repository;

public class InMemoryInventoryManager extends GenericService implements InventoryManager {
    private Map<String, Long> inventoryMap = null;
    private String inventoryName = "In Memory Inventory";
    private String inventoryCountSql;
    private String inventorySql;
    private int batchSize = 10000;
    private Repository inventoryRepository;
    private int timeToLive = 20000;
    private long lastInitTime = -1;

    public String getInventoryCountSql() {
        return inventoryCountSql;
    }

    public void setInventoryCountSql(String inventoryCountSql) {
        this.inventoryCountSql = inventoryCountSql;
    }

    public String getInventorySql() {
        return inventorySql;
    }

    public void setInventorySql(String inventorySql) {
        this.inventorySql = inventorySql;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Repository getInventoryRepository() {
        return inventoryRepository;
    }

    public void setInventoryRepository(Repository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public void doStartService() throws ServiceException {
        super.doStartService();
        if (getInventoryCountSql() == null) {
            throw new ServiceException("Inventory count SQL is required");
        }
        if (getInventorySql() == null) {
            throw new ServiceException("Inventory count SQL is required");
        }
        if (getInventoryRepository() == null) {
            throw new ServiceException("Inventory repository is required");
        }
        loadInventory();
    }

    private void loadInventory() {
        Connection connection = null;
        try {
            connection = ((GSARepository) getInventoryRepository()).getDataSource().getConnection();
            PreparedStatement countStmt = null;
            PreparedStatement inventoryStmt = null;
            try {
                countStmt = connection.prepareStatement(getInventoryCountSql());
                if (countStmt.execute()) {
                    ResultSet rs = countStmt.getResultSet();
                    if (rs.next()) {
                        inventoryStmt = connection.prepareStatement(getInventorySql());
                        loadInventory(inventoryStmt, rs.getInt(1));
                    }
                }
            } catch (SQLException ex) {
                if (isLoggingError()) {
                    logError("Could not initialize in-memory inventory", ex);
                }
            } finally {
                try {
                    if (null != countStmt) {
                        countStmt.close();
                    }
                    if (null != inventoryStmt) {
                        inventoryStmt.close();
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
    
    private void loadInventory(PreparedStatement inventoryStmt, int count) throws SQLException {
        if (count > 0) {
            long startTime = System.currentTimeMillis();
            
            if (isLoggingDebug()) {
                logInfo("Loading " + count + " inventory items into " + getInventoryName());
            }
            
            int offset = 1;
            int hits = getBatchSize();
            int items = 0;
            
            inventoryMap = new HashMap<String, Long>(count);
            inventoryStmt.setInt(1, offset);
            inventoryStmt.setInt(2, hits);
            
            while (inventoryStmt.execute() && offset < count) {
                ResultSet rs = inventoryStmt.getResultSet();

                while (rs.next()) {
                    inventoryMap.put(rs.getString("catalog_ref_id"), rs.getLong("stock_level"));
                    items++;
                }
                if (isLoggingInfo()) {
                    logInfo("Processed " + (items) + " out of " + count);
                }
                rs.close();
                offset += getBatchSize();
                hits += getBatchSize();
                inventoryStmt.clearParameters();
                inventoryStmt.setInt(1, offset);
                inventoryStmt.setInt(2, hits);
            }
            
            lastInitTime = System.currentTimeMillis();
            if (isLoggingInfo()) {
                logInfo("Building trie finished in " + ((lastInitTime - startTime) / 1000)
                        + " seconds. Inventory contains " + inventoryMap.size() + " items");
            }
        }
    }

    private void checkTimeToLive() {
        if (System.currentTimeMillis() - lastInitTime > timeToLive) {
            loadInventory();
        }
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
        throw new UnsupportedOperationException();
    }

    @Override
    public long queryBackorderLevel(String id) throws InventoryException {
        throw new UnsupportedOperationException();
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
        // checkTimeToLive();
        Long stockLevel = inventoryMap.get(id);
        if (stockLevel == null) {
            return 0L;
        }
        return stockLevel;
    }

    @Override
    public long queryStockThreshold(String id) throws InventoryException {
        // TODO Auto-generated method stub
        return 0;
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

}

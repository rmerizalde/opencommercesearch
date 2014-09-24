package org.opencommercesearch.inventory;

import java.io.Serializable;

public class InventoryEntry implements Serializable{

    private static final long serialVersionUID = -7247894936976363331L;
    
    private int availabilityStatus;
    private long backorderLevel;
    private long stockLevel;
    
    public int getAvailabilityStatus() {
        return availabilityStatus;
    }
    public void setAvailabilityStatus(int availabilityStatus) {
        this.availabilityStatus = availabilityStatus;
    }
    public long getBackorderLevel() {
        return backorderLevel;
    }
    public void setBackorderLevel(long backorderLevel) {
        this.backorderLevel = backorderLevel;
    }
    public long getStockLevel() {
        return stockLevel;
    }
    public void setStockLevel(long stockLevel) {
        this.stockLevel = stockLevel;
    }
    
}

package org.opencommercesearch.client.impl;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a sku's availability.
 *
 * @author rmerizalde
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Availability {

  public enum Status {
    InStock,
    OutOfStock,
    PermanentlyOutOfStock,
    Backorderable,
    Preorderable
  }

  private Status status;
  private Long stockLevel;
  private Long backorderLevel;
  private Date date;

  public Date getDate() {
    return date;
  }

  public void setDate(Date date) {
    this.date = date;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public Long getStockLevel() {
    return stockLevel;
  }

  public void setStockLevel(Long stockLevel) {
    this.stockLevel = stockLevel;
  }

  public Long getBackorderLevel() {
    return backorderLevel;
  }

  public void setBackorderLevel(Long backorderLevel) {
    this.backorderLevel = backorderLevel;
  }


}

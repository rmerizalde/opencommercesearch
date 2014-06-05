package org.opencommercesearch.client.request;

import org.apache.commons.lang.StringUtils;
import org.opencommercesearch.client.ProductApi;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a set of parameters than can be passed to {@link org.opencommercesearch.client.ProductApi}.
 *
 * @author jmendez
 */
public interface Request {
  /**
   * Sets a param on the request. If a previous value existed, is replaced with the new one.
   *
   * @param name  The param name.
   * @param value The value for the given param name.
   */
  void setParam(String name, String value);

  /**
   * Gets the value for the given parameter name.
   *
   * @param name The parameter name.
   * @return The value associated to the given parameter name.
   */
  String getParam(String name);

  /**
   * Adds a param value to an existing value. By default, multivalued parameters are comma separated.
   *
   * @param name  The name of the param
   * @param value The new value to add
   */
  void addParam(String name, String value);

  /**
   * Converts this request to a valid query string.
   *
   * @return A query string conformed of all parameters stored in this request.
   */
  String getQueryString();

  /**
   * Gets the endpoint associated to this request.
   *
   * @return Endpoint associated to this request.
   */
  String getEndpoint();

  /**
   * Returns the request method used by this request.
   *
   * @return The request method used by this request.
   */
  ProductApi.RequestMethod getMethod();
}

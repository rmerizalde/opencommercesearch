package org.opencommercesearch.client.response;

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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import org.opencommercesearch.client.impl.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base API response class.
 *
 * @author jmendez
 * @author rmerizalde
 */
public class DefaultResponse implements Response {

  private static Logger logger = LoggerFactory.getLogger(DefaultResponse.class);

  private Metadata metadata;
  private String message;

  public Metadata getMetadata() {
    return metadata;
  }

  protected void setMetadata(Metadata metadata) {
    this.metadata = metadata;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String toString() {
    ObjectWriter ow = new ObjectMapper()
      .setDateFormat(new ISO8601DateFormat())
      .setSerializationInclusion(JsonInclude.Include.NON_NULL)
      .writer().withDefaultPrettyPrinter();

    try {
      return ow.writeValueAsString(this);
    } catch (JsonProcessingException ex) {
      if (logger.isErrorEnabled()) {
        logger.error("Cannot convert to string", ex);
      }
      return "InvalidObject";
    }
  }
}

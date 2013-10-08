package org.opencommercesearch.api;

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

import atg.nucleus.GenericService;
import atg.nucleus.ServiceException;
import org.restlet.Client;
import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Protocol;

import java.util.Map;

/**
 * This class provides helper service to communicate with product API.
 */
public class ProductService extends GenericService {
    public enum Endpoint {
        BRANDS,
        RULES,
        PRODUCTS,
        CATEGORIES,
        FACETS;

        private String lowerCaseName;

        private Endpoint() {
            this.lowerCaseName = name().toLowerCase();
        }

        public String getLowerCaseName() {
            return lowerCaseName;
        }
    }

    private String host = "http://localhost:9000";
    private Map<String, String> endpoints;
    private boolean isPreview = false;
    private Map<String, String> httpSettings;


    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Map<String, String> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(Map<String, String> endpoints) {
        this.endpoints = endpoints;
    }

    public boolean getPreview() {
        return isPreview;
    }

    public void setPreview(boolean isPreview) {
        this.isPreview = isPreview;
    }

    public Map<String, String> getHttpSettings() {
        return httpSettings;
    }

    public void setHttpSettings(Map<String, String> httpSettings) {
        this.httpSettings = httpSettings;
    }

    public String getUrl4Endpoint(Endpoint endpoint) {
        return getUrl4Endpoint(endpoint, null);
    }

    public String getUrl4Endpoint(Endpoint endpoint, String id) {
        String endpointUrl = getEndpoints().get(endpoint.name().toLowerCase());

        if (endpointUrl == null) {
            throw new IllegalArgumentException(endpoint.name());
        }

        endpointUrl = getHost() + endpointUrl;
        if (id != null) {
            endpointUrl += "/" + id;
        }
        endpointUrl += (getPreview()? "?preview=true": "");
        return endpointUrl;
    }

    private Client createClient() {
        Context context = new Context();
        if (getHttpSettings() != null) {
            for (Map.Entry<String, String> entry : getHttpSettings().entrySet()) {
                context.getParameters().add(entry.getKey(), entry.getValue());
            }
        }

        Client client = new Client(context, Protocol.HTTP);
        try {
            client.start();
            return client;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public Response handle(Request request) {
        Client client = createClient();

        try {
            return client.handle(request);
        } finally {
            try {
                client.stop();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

}

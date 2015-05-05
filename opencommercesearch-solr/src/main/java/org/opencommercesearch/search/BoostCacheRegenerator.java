package org.opencommercesearch.search;

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

import org.apache.solr.search.CacheRegenerator;
import org.apache.solr.search.LRUCache;
import org.apache.solr.search.SolrCache;
import org.apache.solr.search.SolrIndexSearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Boosts are retrieved from a external source. We don't rely on the new index data so
 * we simply copy the entries from the old cache to the new one.
 *
 * Optionally, the regenerator can be configured to purge all entries when a certain
 * amount time has been exceed.
 *
 * @author rmerizalde
 */
public class BoostCacheRegenerator implements CacheRegenerator {

    private static Logger log = LoggerFactory.getLogger(BoostCacheRegenerator.class);

    private long ttlMs = 60 * 60 * 1000;
    private long loadTime = System.currentTimeMillis();

    public BoostCacheRegenerator() {
        String ttl = System.getProperty("boost.ttl");

        if (ttl != null) {
            try {
                ttlMs = Long.parseLong(ttl);
                log.info("Setting boosts ttl to " + ttlMs);
            } catch (NumberFormatException ex) {
                log.error("Invalid boost ttl " + ttl);
            }
        }
    }

    public boolean regenerateItem(SolrIndexSearcher newSearcher, SolrCache newCache, SolrCache oldCache, Object oldKey, Object oldVal) throws IOException {
        long time = System.currentTimeMillis();

        if (time - loadTime > ttlMs) {
            log.info("Boost ttl exceeded, stopping regeneration");
            newCache.clear();
            loadTime = time;
            return false;
        }
        newCache.put(oldKey, oldVal);
        return true;
    }
}

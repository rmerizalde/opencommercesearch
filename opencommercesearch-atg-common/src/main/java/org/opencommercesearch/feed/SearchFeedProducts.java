package org.opencommercesearch.feed;

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

import java.util.*;

import org.opencommercesearch.client.Product;

public class SearchFeedProducts {
    private Map<Locale, List<Product>> productsByLocale = new HashMap<Locale, List<Product>>();
    // failures are counted by product. If a product fails for 1 or more locales only one failure is counted
    Set<String> failedProducts = new HashSet<String>();

    /**
     * Searches for a product ID with the given locale.
     * @param locale The locale
     * @param productId The product ID to look for.
     * @return The product in the given locale if exists, null otherwise.
     */
    public Product getProduct(Locale locale, String productId) {
        if (locale != null && productId != null) {
            List<Product> productList = productsByLocale.get(locale);

            if (productList != null) {
                for (Product product : productList) {
                    if(productId.equals(product.getId())) {
                        return product;
                    }
                }
            }
        }

        return null;
    }

    public void add(Locale locale, Product product) {
        List<Product> productList = productsByLocale.get(locale);

        if (productList == null) {
            productList = new ArrayList<Product>();
            productsByLocale.put(locale, productList);
        }

        productList.add(product);
    }

    public Set<Locale> getLocales() {
        return productsByLocale.keySet();
    }

    public List<Product> getProducts(Locale locale) {
        return productsByLocale.get(locale);
    }

    /**
     * Gets the total product count in the current instance, considering all locales.
     * @return Total product count in the current instance (counts all locales).
     */
    public int getProductCount() {
        int count = 0;
        for(Locale locale : getLocales()) {
            count += productsByLocale.get(locale).size();
        }

        return count;
    }

    public int getSkuCount(Locale locale) {
        int count = 0;
        for (Product p : productsByLocale.get(locale)) {
            if (p.getSkus() != null) {
                count += p.getSkus().size();
            }
        }
        return count;
    }

    public int getMaxSkuCount() {
        int maxCount = 0;
        for (Locale locale : getLocales()) {
            maxCount = Math.max(maxCount, getSkuCount(locale));
        }
        return maxCount;
    }


    public void clear() {
        for (Locale locale : getLocales()) {
            productsByLocale.get(locale).clear();
        }
        failedProducts.clear();
    }

    void addFailure(Product product) {
        failedProducts.add(product.getId());
    }

    Set<String> getFailedProducts() {
        return failedProducts;
    }

    void clearFailures() {
        failedProducts.clear();
    }
}

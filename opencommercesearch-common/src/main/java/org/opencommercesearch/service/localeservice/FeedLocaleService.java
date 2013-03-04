package org.opencommercesearch.service.localeservice;

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

import atg.service.localeservice.LocaleService;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * Overrides ATG's local service to allow jobs running out of servlet request and without a user profile change the
 * current locale.
 *
 * This class is not thread safe and each feed should have its own instance.
 *
 * @rmerizalde
 */
public class FeedLocaleService extends LocaleService {

    private LinkedList<Locale> stack = new LinkedList<Locale>();
    private List<String> supportedLocaleKeys;
    private List<Locale> supportedLocales;
    private Locale[] NO_LOCALES = new Locale[]{};

    public String[] getSupportedLocaleKeys() {
        String[] supportedLocaleKeysArray = new String[supportedLocaleKeys.size()];
        return supportedLocaleKeys.toArray(supportedLocaleKeysArray);
    }

    public void setSupportedLocaleKeys(String[] supportedLocaleKeys) {
        this.supportedLocaleKeys = new ArrayList<String>(supportedLocaleKeys.length);
        for (String localeKey : supportedLocaleKeys) {
            this.supportedLocaleKeys.add(localeKey);
        }
        buildLocaleCache();
    }

    public void pushLocale(Locale locale) {
        stack.addFirst(locale);
    }

    public Locale popLocale() {
        if (stack.size() == 0) {
            return null;
        }
        return stack.removeFirst();
    }

    @Override
    public Locale getLocale() {
        if (stack.size() == 0) {
            return Locale.US;
        }
        return stack.getFirst();
    }

    public Locale[] getSupportedLocales() {
        if (supportedLocales == null) {
            return NO_LOCALES;
        }

        Locale[] supportedLocaleArray = new Locale[supportedLocales.size()];
        return supportedLocales.toArray(supportedLocaleArray);
    }

    public int getSupportedLocaleCount() {
        return (supportedLocales != null)? supportedLocales.size() : 0;
    }

    private void buildLocaleCache() {
        supportedLocales = new LinkedList<Locale>();

        for (String localeKey : supportedLocaleKeys) {
            String[] parts = StringUtils.split(localeKey, '_');
            if (parts.length >= 2) {
                supportedLocales.add(new Locale(parts[0], parts[1]));
            }
        }
    }

    public boolean isSupportedLocale(String language, String country) {
        for (Locale locale : supportedLocales) {
            if (locale.getLanguage().equals(language) && locale.getCountry().equals(country)) {
                return true;
            }
        }
        return false;
    }



}

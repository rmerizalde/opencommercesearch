package org.opencommercesearch.service.localservice;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.opencommercesearch.service.localeservice.FeedLocaleService;


import java.util.*;

/**
 * @rmerizalde
 */
public class FeedLocaleServiceTest {
    private Set<Locale> expectedLocales;

    @Before
    public void setup() {
        expectedLocales = new HashSet<Locale>();
        expectedLocales.add(new Locale("en", "US"));
        expectedLocales.add(new Locale("es", "CR"));
        expectedLocales.add(new Locale("fr", "FR"));
    }


    @Test
    public void testPush() {
        FeedLocaleService localeService = new FeedLocaleService();

        assertEquals(Locale.US, localeService.getLocale());
        localeService.pushLocale(Locale.CANADA_FRENCH);
        assertEquals(Locale.CANADA_FRENCH, localeService.getLocale());
        localeService.popLocale();
    }

    @Test
    public void testPop() {
        FeedLocaleService localeService = new FeedLocaleService();

        assertEquals(Locale.US, localeService.getLocale());
        localeService.pushLocale(Locale.CANADA_FRENCH);
        assertEquals(Locale.CANADA_FRENCH, localeService.getLocale());
        localeService.popLocale();
        assertEquals(Locale.US, localeService.getLocale());
    }

    @Test
    public void testDefault() {
        FeedLocaleService localeService = new FeedLocaleService();

        assertEquals(Locale.US, localeService.getLocale());
        localeService.popLocale();
        assertEquals(Locale.US, localeService.getLocale());
    }

    @Test
    public void testLocales() {
        FeedLocaleService localeService = new FeedLocaleService();
        String[] localeKeys = new String[] {"en_US", "es_CR", "fr_FR"};
        localeService.setSupportedLocaleKeys(localeKeys);
        assertEquals(3, localeService.getSupportedLocaleCount());
        verifyLocales(localeService);
    }

    @Test
    public void testChangeLocales() {
        FeedLocaleService localeService = new FeedLocaleService();

        String[] localeKeys = new String[] {"en_US", "es_CR", "fr_FR"};
        localeService.setSupportedLocaleKeys(localeKeys);
        assertEquals(3, localeService.getSupportedLocaleCount());
        localeKeys = new String[] {"en_US", "fr_FR"};
        localeService.setSupportedLocaleKeys(localeKeys);
        assertEquals(2, localeService.getSupportedLocaleCount());
        verifyLocales(localeService);
    }

    @Test
    public void testNoLocales() {
        FeedLocaleService localeService = new FeedLocaleService();
        assertEquals(0, localeService.getSupportedLocaleCount());
        assertEquals(new Locale[]{}, localeService.getSupportedLocales());
    }

    @Test
    public void testIsSupportedLocale() {
        FeedLocaleService localeService = new FeedLocaleService();

        String[] localeKeys = new String[] {"en_US", "es_CR", "fr_FR"};

        localeService.setSupportedLocaleKeys(localeKeys);
        assertTrue(localeService.isSupportedLocale("en", "US"));
        assertTrue(localeService.isSupportedLocale("es", "CR"));
        assertTrue(localeService.isSupportedLocale("fr", "FR"));
        assertFalse(localeService.isSupportedLocale("fr", "CA"));
        assertFalse(localeService.isSupportedLocale("es", "US"));
        assertFalse(localeService.isSupportedLocale("en", "CR"));
        assertFalse(localeService.isSupportedLocale("", ""));
        assertFalse(localeService.isSupportedLocale(null, null));
    }

    private void verifyLocales(FeedLocaleService localeService) {
        Locale[] supportedLocalesKeys = localeService.getSupportedLocales();

        for (Locale locale : supportedLocalesKeys) {
            assertTrue("Not expecting locale " + locale.toString(), expectedLocales.contains(locale));
        }
    }


}

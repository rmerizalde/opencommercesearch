package org.opencommercesearch.service.localservice;

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

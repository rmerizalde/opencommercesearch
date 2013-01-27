package org.opencommercesearch.service.localservice;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.opencommercesearch.service.localeservice.FeedLocaleService;


import java.util.Locale;

/**
 * @rmerizalde
 */
public class FeedLocaleServiceTest {

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

}

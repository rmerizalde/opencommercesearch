package org.opencommercesearch.service.localeservice;

import atg.service.localeservice.LocaleService;

import java.util.LinkedList;
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

}

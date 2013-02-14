package org.opencommercesearch.service.localeservice;

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

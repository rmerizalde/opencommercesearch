package org.commercesearch;

import java.util.ResourceBundle;

import org.apache.commons.lang.StringUtils;

public class Utils {
    public static final String PATH_SEPARATOR = "_";
    public static final String RESOURCE_IN_RANGE = "inrange";
    public static final String RESOURCE_BEFORE = "before";
    public static final String RESOURCE_AFTER = "after";

    public static final ResourceBundle resources = ResourceBundle.getBundle("org.commercesearch.CSResources");

    public static String createPath(FilterQuery[] filterQueries, FilterQuery skipFilter) {
        if (filterQueries == null) {
            return StringUtils.EMPTY;
        }

        StringBuffer b = new StringBuffer();

        for (FilterQuery filterQuery : filterQueries) {
            if (!filterQuery.equals(skipFilter)) {
                b.append(filterQuery.toString()).append(PATH_SEPARATOR);
            }
        }
        if (b.length() > 0) {
            b.setLength(b.length() - 1);
        }
        return b.toString();
    }

    public static String getRangeName(String fieldName, String key, String value1, String value2) {
        String resource = resources.getString("facet.range." + fieldName + "." + key);

        if (resource == null) {
            resource = "${v1}-${v2}";
        }

        String rangeName = StringUtils.replace(resource, "${v1}", (value1 == null ? "" : value1));
        rangeName = StringUtils.replace(rangeName, "${v2}", (value2 == null ? "" : value2));
        return rangeName;
    }
}

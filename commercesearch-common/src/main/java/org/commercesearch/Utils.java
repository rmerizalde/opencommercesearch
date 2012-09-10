package org.commercesearch;

import org.apache.commons.lang.StringUtils;

public class Utils {
    public static String PATH_SEPARATOR = "_";

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
}

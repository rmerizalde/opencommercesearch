package org.apache.solr.handler.component;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.apache.solr.handler.component.GroupCollapseComponent.COLOR_FIELD;
import static org.apache.solr.handler.component.GroupCollapseComponent.COLORFAMILY_FIELD;

import org.apache.solr.common.util.NamedList;

class ColorGroupFieldSummary extends GroupFieldSummary<String> {
    private Set<String> colors;

    ColorGroupFieldSummary(String groupValue, String fieldName) {
        super(groupValue, fieldName);
        colors = new LinkedHashSet<String>();
    }

    void processFieldValue(String val) {
        colors.add(val);
    }

    void addValues(NamedList rsp) {
        if (fieldName.equals(COLOR_FIELD)) {
            rsp.add("count", colors.size());
        } else if (fieldName.equals(COLORFAMILY_FIELD)) {
            rsp.add("families", colors);
        } else {
            rsp.add(fieldName, colors);
        }
    }
}

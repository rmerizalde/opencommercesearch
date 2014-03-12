package org.apache.solr.handler.component;

import org.apache.solr.common.util.NamedList;

public class FloatGroupFieldSummary extends GroupFieldSummary<Float> {
    private float min;
    private float max;

    FloatGroupFieldSummary(String groupValue, String fieldName) {
        super(groupValue, fieldName);
        min = Float.POSITIVE_INFINITY;
        max = Float.NEGATIVE_INFINITY;
    }

    void processFieldValue(Float val) {
        min = Math.min(min, val);
        max = Math.max(max, val);
    }

    void addValues(NamedList rsp) {
        rsp.add("min", min);
        rsp.add("max", max);
    }
}
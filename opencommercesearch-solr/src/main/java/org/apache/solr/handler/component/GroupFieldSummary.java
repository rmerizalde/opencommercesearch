package org.apache.solr.handler.component;

import org.apache.solr.common.util.NamedList;

abstract class GroupFieldSummary<T> {
    String groupValue;
    String fieldName;

    GroupFieldSummary(String groupValue, String fieldName) {
        this.groupValue = groupValue;
        this.fieldName = fieldName;
    }

    abstract void processFieldValue(T val);

    abstract void addValues(NamedList rsp);
}

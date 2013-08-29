package org.apache.solr.handler.component;

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

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.solr.common.params.GroupCollapseParams;
import org.apache.solr.core.SolrCore;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocSlice;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.util.SolrPluginUtils;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * @author rmerizalde
 */
public class GroupCollapseComponent extends SearchComponent implements SolrCoreAware {
    private static Logger log = LoggerFactory.getLogger(GroupCollapseComponent.class);

    private SolrParams initArgs = null;

    @Override
    public void init(NamedList args)
    {
        this.initArgs = SolrParams.toSolrParams(args);
    }

    public void inform(SolrCore core) {
        // do nothing
    }

    @Override
    public String getDescription() {
        return "Group Collapse - collapse particular grouped document into a single document";
    }

    @Override
    public String getSource() {
        return "https://raw.github.com/rmerizalde/opencommercesearch/master/opencommercesearch-solr/src/main/java/org/apache/solr/handler/component/GroupCollapseComponent.java";
    }

    @Override
    public void prepare(ResponseBuilder rb) throws IOException {
    }

    @Override
    public void process(ResponseBuilder rb) throws IOException {
        if (!rb.grouping()) {
            return;
        }

        SolrParams params = rb.req.getParams();

        if (!params.getBool(GroupCollapseParams.GROUP_COLLAPSE, false)) {
            return;
        }

        String fieldList = params.get(GroupCollapseParams.GROUP_COLLAPSE_FL);

        if (fieldList == null) {
            return;
        }

        String[] wantedFields = SolrPluginUtils.split(fieldList);
        Set<String> fieldNames = new HashSet<String>();
        IndexSchema schema = rb.req.getSchema();

        for (String wantedField : wantedFields) {
            FieldType fieldType = schema.getFieldType(wantedField);

            if (fieldType.getNumericType() != null) {
                fieldNames.add(wantedField);
            }
            else {
                log.warn("Unsupported field summary type: " + fieldType.getTypeName());
            }
        }

        String filterField = params.get(GroupCollapseParams.GROUP_COLLAPSE_FF);

        if(filterField != null) {
            FieldType fieldType = schema.getFieldType(filterField);

            if(!fieldType.getTypeName().equals("boolean")) {
              log.warn("Group collapse filter field is not boolean, no filtering will be done. Check if the param GROUP_COLLAPSE_EF is correct.");
              filterField = null;
            }
        }

        if (fieldNames.size() == 0) {
            log.info("No query response found");
            return;
        }

        if (rb.rsp == null) {
            log.info("No query response found");
            return;
        }

        NamedList namedList = rb.rsp.getValues();

        if (namedList == null) {
            log.info("No values found in query response");
            return;
        }

        namedList = (NamedList) namedList.get("grouped");

        NamedList groupSummaryRsp = new NamedList();

        for (String field : rb.getGroupingSpec().getFields()) {
            GroupCollapseSummary groupFieldSummary = new GroupCollapseSummary(field, rb.req.getSearcher(), fieldNames, filterField);

            NamedList groupField = (NamedList) namedList.get(field);

            if (groupField != null) {
                groupFieldSummary.processGroupField(groupField);
                groupFieldSummary.addValues(groupSummaryRsp);
            }
        }

        rb.rsp.add("groups_summary", groupSummaryRsp);
    }

    private class GroupCollapseSummary {
        private String groupField;
        private SolrIndexSearcher searcher;
        private Set<String> fieldNames;
        private Map<String, Map<String, GroupFieldSummary>> groupFieldSummaries;
        private String filterField;

        /**
         * Default constructor. Creates a new group collapse summary.
         * @param groupField Field used to create the group.
         * @param searcher Valid Solr index search instance.
         * @param fieldNames List of fields that will be "summarized" by this class.
         * @param filterField Field that is used to filter docs in the group summary calculation. See {@link GroupCollapseParams#GROUP_COLLAPSE_FF} for details.
         */
        GroupCollapseSummary(String groupField, SolrIndexSearcher searcher, Set<String> fieldNames, String filterField) {
            this.groupField = groupField;
            this.searcher = searcher;
            this.fieldNames = fieldNames;
            groupFieldSummaries = new HashMap<String, Map<String, GroupFieldSummary>>();
            this.filterField = filterField;
        }

        private void processGroupField(NamedList groupField) throws IOException {
            log.debug("Processing group field: " + groupField);
            List<NamedList> groups = (List<NamedList>) groupField.get("groups");

            if (groups == null) {
                log.debug("No groups found for: " + groupField);
                return;
            }

            for (NamedList group : groups) {
                if (group != null) {
                    String groupValue = (String) group.get("groupValue");
                    DocSlice docSlice = (DocSlice) group.get("doclist");
                    boolean shouldReprocess = processDocs(groupValue, docSlice, true);

                    //If all docs in the group were filtered, then reprocess without applying any filtering.
                    if(shouldReprocess) {
                        processDocs(groupValue, docSlice, false);
                    }

                    group.remove("doclist");
                    group.add("doclist", docSlice.subset(0, 1));
                }
            }
        }

        /**
         * Process docs within the group. All wanted fields for this each doc will be summarized into a single group value.
         * <p/>
         * If a filter field was specified and filter docs is true, then docs with filter field set to "true" will be excluded from the process. See {@link GroupCollapseParams#GROUP_COLLAPSE_FF} for details.
         * @param groupValue The current group field value.
         * @param docSlice Docs within the current group field value.
         * @param filterDocs Whether or not do filtering of docs based on filterField.
         * @return True if all documents in the given docSlice were filtered. False if at least one document was processed.
         * @throws IOException If doc fields can't be retrieved from the index.
         */
        private boolean processDocs(String groupValue, DocSlice docSlice, boolean filterDocs) throws IOException {
            boolean allFiltered = true;

            for (DocIterator it = docSlice.iterator(); it.hasNext();) {
                Document doc = searcher.doc(it.nextDoc(), fieldNames);
                IndexableField filterField = doc.getField(this.filterField);

                for (String fieldName : fieldNames) {
                    GroupFieldSummary summary = getSummary(groupValue, fieldName);
                    IndexableField indexableField = doc.getField(fieldName);

                    if(!filterDocs || filterField == null || filterField.stringValue().equals("F")) {
                        allFiltered = false;
                        if (indexableField != null) {
                            summary.processFieldValue(indexableField.numericValue().floatValue());
                        }
                    }
                }
            }

            return allFiltered;
        }

        private GroupFieldSummary getSummary(String groupValue, String fieldName) {
            Map<String, GroupFieldSummary> groupValueSummaries = groupFieldSummaries.get(groupValue);

            if (groupValueSummaries == null) {
                groupValueSummaries = new HashMap<String, GroupFieldSummary>();
                groupFieldSummaries.put(groupValue, groupValueSummaries);
            }

            GroupFieldSummary summary = groupValueSummaries.get(fieldName);

            if (summary == null) {
                summary = new GroupFieldSummary(groupValue, fieldName);
                groupValueSummaries.put(fieldName, summary);
            }

            return summary;
        }

        void addValues(NamedList rsp) {
            NamedList groupFieldRsp = new NamedList();
            for (Map.Entry<String, Map<String, GroupFieldSummary>> entry : groupFieldSummaries.entrySet()) {
                NamedList groupValueRsp = new NamedList();
                for (Map.Entry<String, GroupFieldSummary> innerEntry : entry.getValue().entrySet()) {
                    NamedList fieldSummaryRsp = new NamedList();
                    innerEntry.getValue().addValues(fieldSummaryRsp);
                    groupValueRsp.add(innerEntry.getKey(), fieldSummaryRsp);
                }
                groupFieldRsp.add(entry.getKey(), groupValueRsp);
            }
            rsp.add(groupField, groupFieldRsp);
        }
    }

    // TODO support types other than double
    private class GroupFieldSummary {
        String groupValue;
        String fieldName;
        float min;
        float max;

        GroupFieldSummary(String groupValue, String fieldName) {
            this.groupValue = groupValue;
            this.fieldName = fieldName;
            min = Float.POSITIVE_INFINITY;
            max = Float.NEGATIVE_INFINITY;
        }

        void processFieldValue(float val) {
            min = Math.min(min, val);
            max = Math.max(max, val);
        }

        void addValues(NamedList rsp) {
            rsp.add("min", min);
            rsp.add("max", max);
        }
    }
}

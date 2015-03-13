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

import org.apache.lucene.search.Query;
import org.apache.solr.common.params.ExpandParams;
import org.apache.solr.common.params.GroupCollapseParams;
import org.apache.solr.core.SolrCore;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.search.CollapsingQParserPlugin;
import org.apache.solr.search.DocList;
import org.apache.solr.search.QParser;
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
    public static String COLOR_FIELD = "color";
    public static String COLORFAMILY_FIELD = "colorFamily";

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
        if (!rb.grouping() && !rb.req.getParams().getBool("expandall",false)) {
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
        String[] fqs = params.getParams(GroupCollapseParams.GROUP_COLLAPSE_FQ);

        for (String wantedField : wantedFields) {
            FieldType fieldType = schema.getFieldType(wantedField);

            // @todo: this won't be need when we stop supporting the old summary generation approach
            if (fieldType.getNumericType() != null || fieldType.getTypeName().equals("string") || fqs != null) {
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
              log.warn("Group collapse filter field is not boolean, no filtering will be done. Check if the param GROUP_COLLAPSE_FF is correct.");
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

        NamedList values = rb.rsp.getValues();

        if (values == null) {
            log.info("No values found in query response");
            return;
        }

        NamedList grouped = (NamedList) values.get("grouped");

        if (grouped != null) {
            if (fqs != null) {
                List<Query> newFilters = new ArrayList<Query>();
                try {
                    for (String fq : fqs) {
                        if (fq != null && fq.trim().length() != 0 && !fq.equals("*:*")) {
                            QParser fqp = QParser.getParser(fq, null, rb.req);
                            newFilters.add(fqp.getQuery());
                        }
                    }
                } catch (Exception e) {
                    throw new IOException(e);
                }

                if (rb.getGroupingSpec().getFields().length > 0) {
                    String field = rb.getGroupingSpec().getFields()[0];
                    GroupCollapseSummary groupFieldSummary = new GroupCollapseSummary(field, rb.req.getSearcher(), fieldNames, filterField);
                    NamedList groupSummaryRsp = new NamedList();
                    Map<String, DocList> expanded = groupsToMap((NamedList) grouped.get(field));

                    groupFieldSummary.processExpandedOrds(expanded, newFilters, groupSummaryRsp, params);
                    rb.rsp.add("groups_summary", groupSummaryRsp);
                }
            } else {

                NamedList groupSummaryRsp = new NamedList();

                for (String field : rb.getGroupingSpec().getFields()) {
                    GroupCollapseSummary groupFieldSummary = new GroupCollapseSummary(field, rb.req.getSearcher(), fieldNames, filterField);

                    NamedList groupField = (NamedList) grouped.get(field);

                    if (groupField != null) {
                        groupFieldSummary.processGroupField(groupField);
                        groupFieldSummary.addValues(groupSummaryRsp);
                    }
                }
                rb.rsp.add("groups_summary", groupSummaryRsp);
            }

        } else {
            Map<String, DocList> expanded = (Map<String, DocList>) values.get("expanded");
            NamedList groupSummaryRsp = new NamedList();

            if (expanded != null) {
                GroupCollapseSummary groupFieldSummary = new GroupCollapseSummary(getExpandField(params, rb), rb.req.getSearcher(), fieldNames, filterField);

                if (fqs != null) {
                    List<Query> newFilters = new ArrayList<Query>();
                    try {
                        for (String fq : fqs) {
                            if (fq != null && fq.trim().length() != 0 && !fq.equals("*:*")) {
                                QParser fqp = QParser.getParser(fq, null, rb.req);
                                newFilters.add(fqp.getQuery());
                            }
                        }
                    } catch (Exception e) {
                        throw new IOException(e);
                    }


                    groupFieldSummary.processExpandedOrds(expanded, newFilters, groupSummaryRsp, params);
                } else {
                    groupFieldSummary.processExpanded(expanded);
                    groupFieldSummary.addValues(groupSummaryRsp);
                }

                rb.rsp.add("groups_summary", groupSummaryRsp);
            } else {
                log.info("No groups found in query response");
            }
        }
    }

    private Map<String, DocList> groupsToMap(NamedList groupField) {
        log.debug("Converting group field to map: %s", groupField);
        List<NamedList> groups = (List<NamedList>) groupField.get("groups");

        if (groups == null || groups.size() == 0) {
            log.debug("No groups found for: %s", groupField);
            return Collections.emptyMap();
        }

        Map<String, DocList> map = new HashMap<String, DocList>(groups.size());
        for (NamedList group : groups) {
            if (group != null) {
                String groupValue = (String) group.get("groupValue");
                DocList docList = (DocList) group.get("doclist");
                map.put(groupValue, docList);
            }
        }
        return map;
    }

    private String getExpandField(SolrParams params, ResponseBuilder rb) {
        String field = params.get(ExpandParams.EXPAND_FIELD);
        if(field == null) {
          List<Query> filters = rb.getFilters();
          if(filters != null) {
            for(Query q : filters) {
              if(q instanceof CollapsingQParserPlugin.CollapsingPostFilter) {
                  CollapsingQParserPlugin.CollapsingPostFilter cp = (CollapsingQParserPlugin.CollapsingPostFilter)q;
                  field = cp.getField();
              }
            }
          }
        }
        return field;
    }
    
}

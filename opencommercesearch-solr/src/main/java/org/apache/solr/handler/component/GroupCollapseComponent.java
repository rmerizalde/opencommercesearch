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

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;
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
            
            if (fieldType.getNumericType() != null || fieldType.getTypeName().equals("string")) {
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

        NamedList namedList = rb.rsp.getValues();

        if (namedList == null) {
            log.info("No values found in query response");
            return;
        }

        namedList = (NamedList) namedList.get("grouped");

        if (namedList == null) {
            log.info("No groups found in query response");
            return;
        }
        
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
    
}

package org.opencommercesearch;

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

import static org.junit.Assert.assertEquals;

import org.apache.solr.client.solrj.request.FieldAnalysisRequest;
import org.apache.solr.common.util.NamedList;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.opencommercesearch.junit.runners.SearchJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.Locale;


@Category(IntegrationSearchTest.class)
@RunWith(SearchJUnit4ClassRunner.class)
public abstract class SchemaIntegrationTest {
    public abstract Locale getLocale();

    protected Analysis analyzeFieldType(SearchServer server, String fieldType, String fieldValue)
            throws SearchServerException {
        FieldAnalysisRequest request = new FieldAnalysisRequest();
        request.addFieldType(fieldType);
        request.setFieldValue(fieldValue);
        request.setQuery(fieldValue);

        return analyze(server, request, "field_types", fieldType);
    }

    protected Analysis analyzeFieldName(SearchServer server, String fieldName, String fieldValue)
            throws SearchServerException {
        FieldAnalysisRequest request = new FieldAnalysisRequest();
        request.addFieldName(fieldName);
        request.setFieldValue(fieldValue);
        request.setQuery(fieldValue);

        return analyze(server, request, "field_names", fieldName);
    }

    protected Analysis analyze(SearchServer server, FieldAnalysisRequest request, String fields_key, String field_key)
            throws SearchServerException {
        NamedList<Object> res = server.analyze(request, getLocale());
        NamedList<Object> analysis = (NamedList<Object>) res.get("analysis");
        NamedList<Object> fields = (NamedList<Object>) analysis.get(fields_key);
        NamedList<Object> field = (NamedList<Object>) fields.get(field_key);
        NamedList<Object> queryAnalysis = (NamedList<Object>) field.get("query");
        NamedList<Object> indexAnalysis = (NamedList<Object>) field.get("index");

        return new Analysis(
                (ArrayList<NamedList<Object>>) queryAnalysis.getVal(queryAnalysis.size() - 1),
                (ArrayList<NamedList<Object>>) indexAnalysis.getVal(indexAnalysis.size() - 1)
        );
    }

    protected static class Analysis {
        private ArrayList<NamedList<Object>> queryWords;
        private ArrayList<NamedList<Object>> indexWords;

        Analysis(ArrayList<NamedList<Object>> queryWords, ArrayList<NamedList<Object>> indexWords) {
            this.queryWords = queryWords;
            this.indexWords = indexWords;
        }

        public ArrayList<NamedList<Object>> getQueryWords() {
            return queryWords;
        }

        public ArrayList<NamedList<Object>> getIndexWords() {
            return indexWords;
        }

        public ArrayList<NamedList<Object>>[] getWords() {
            return new ArrayList[] {queryWords, indexWords};
        }
    }
}

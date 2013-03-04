package org.opencommercesearch.lucene.queries.function.valuesource;

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

import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.UnicodeUtil;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.schema.StrField;
import org.apache.solr.schema.TextField;
import org.apache.solr.search.FunctionQParser;
import org.apache.solr.search.ValueSourceParser;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 @author rmerizalde
 */
public class FixedBoostValueSourceParser extends ValueSourceParser {

    private static final int DEFAULT_BOOST_COUNT = 10;

    @Override
    public void init(NamedList namedList) {
    }

    @Override
    public ValueSource parse(FunctionQParser fp) throws ParseException {
        String field = fp.parseArg();

        SchemaField f = fp.getReq().getSchema().getField(field);
        ValueSource fieldValueSource = f.getType().getValueSource(f, fp);
        Map<String, Integer> positions = new HashMap<String, Integer>(DEFAULT_BOOST_COUNT);
        int position = 0;

        while (fp.hasMoreArguments()) {
            positions.put(fp.parseArg(), position++);
        }
        return new FixedBoostValueSource(field, fieldValueSource, positions);
    }
}

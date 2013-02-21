package org.opencommercesearch.lucene.queries.function.valuesource;

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

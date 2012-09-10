package org.commercesearch.lucene.queries.function.valuesource;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 @author rmerizalde
 */
public class FixedBoostValueSourceParser extends ValueSourceParser {
    @Override
    public void init(NamedList namedList) {
    }

    @Override
    public ValueSource parse(FunctionQParser fp) throws ParseException {
        String field = fp.parseArg();
        List<String> values = new ArrayList<String>();
        SchemaField f = fp.getReq().getSchema().getField(field);
        ValueSource fieldValueSource = f.getType().getValueSource(f, fp);

        while (fp.hasMoreArguments()) {
            values.add(fp.parseArg());
        }
        Collections.reverse(values);
        return new FixedBoostValueSource(field, fieldValueSource, values);
    }
}

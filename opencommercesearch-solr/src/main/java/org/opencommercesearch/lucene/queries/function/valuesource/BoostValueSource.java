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

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.docvalues.FloatDocValues;
import org.apache.lucene.queries.function.docvalues.IntDocValues;

import java.io.IOException;
import java.util.Map;

/**
 * Value source for dynamic boosts. A dynamic boost can be associated with a product or a sku. The function first parameter
 * is the field name to be used as the boost key.
 *
 * @author rmerizalde
 */
public class BoostValueSource extends ValueSource {
    private final String field;
    private final ValueSource fieldValueSource;
    private final Map<String, Float> boosts;

    /**
     * Create a new value source
     *
     * @param field is the field use as the boost value key (e.g. sku or product id)
     * @param fieldValueSource is the value source to retrieve the boost key values
     * @param boosts is the boost map
     */
    public BoostValueSource(String field, ValueSource fieldValueSource, Map<String, Float> boosts) {
        this.field = field;
        this.fieldValueSource = fieldValueSource;
        this.boosts = boosts;
    }

    @Override
    public FunctionValues getValues(Map context, AtomicReaderContext readerContext) throws IOException {
        final FunctionValues vals = fieldValueSource.getValues(context, readerContext);

        return new FloatDocValues(this) {
            @Override
            public float floatVal(int doc) {
                float boost = 0.0f;

                String value = vals.strVal(doc);
                Float b = boosts.get(value);
                if (b != null) {
                    boost = b;
                }
                return boost;
            }
        };
    }


    @Override
    public boolean equals(Object o) {
        if (this.getClass() != o.getClass()) return false;
        BoostValueSource other = (BoostValueSource) o;
        return field.equals(other.field) && fieldValueSource.equals(other.fieldValueSource) && boosts.equals(other.boosts);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + field.hashCode() + fieldValueSource.hashCode() + boosts.hashCode();
    }

    public String name() {
        return "boost";
    }

    @Override
    public String description() {
        return name() + '(' + field + "," + boosts.keySet() + ')';
    }
}

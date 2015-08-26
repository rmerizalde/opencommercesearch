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
import org.apache.lucene.queries.function.docvalues.IntDocValues;

import java.io.IOException;
import java.util.Map;

/**
 * @rmerizalde
 */
public class FixedBoostValueSource extends ValueSource {
    private final String field;
    private final ValueSource fieldValueSource;
    private final Map<String, Integer> positions;

    public FixedBoostValueSource(String field, ValueSource fieldValueSource, Map<String, Integer> positions) {
        this.field = field;
        this.fieldValueSource = fieldValueSource;
        this.positions = positions;
    }

  @Override
  public FunctionValues getValues(Map context, AtomicReaderContext readerContext) throws IOException {
      final FunctionValues vals = fieldValueSource.getValues(context, readerContext);

      return new IntDocValues(this) {
          @Override
          public int intVal(int doc) {
              int position = positions.size();

              String value = vals.strVal(doc);
              Integer index = positions.get(value);
              if (index != null) {
                  position = index;
              }
              return position;
          }
      };
  }


    @Override
    public boolean equals(Object o) {
        if (this.getClass() != o.getClass()) return false;
        FixedBoostValueSource other = (FixedBoostValueSource) o;
        return field.equals(other.field) && fieldValueSource.equals(other.fieldValueSource) && positions.equals(other.positions);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + field.hashCode() + fieldValueSource.hashCode() + positions.hashCode();
    }

    public String name() {
        return "fixedBoost";
    }

    @Override
    public String description() {
        return name() + '(' + field + "," + positions.keySet() + ')';
    }
}

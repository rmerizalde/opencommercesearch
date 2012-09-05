package org.commercesearch.lucene.queries.function.valuesource;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.index.*;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.docvalues.FloatDocValues;
import org.apache.lucene.queries.function.docvalues.IntDocValues;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.FieldCache;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.mutable.MutableValueInt;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @rmerizalde
 */
public class FixedBoostValueSource extends ValueSource {
    private final String field;
    private final ValueSource fieldValueSource;
    private final List<String> values;

    public FixedBoostValueSource(String field, ValueSource fieldValueSource, List<String> values) {
        this.field = field;
        this.fieldValueSource = fieldValueSource;
        this.values = values;
    }

  @Override
  public FunctionValues getValues(Map context, AtomicReaderContext readerContext) throws IOException {
      final FunctionValues vals = fieldValueSource.getValues(context, readerContext);

      return new IntDocValues(this) {
          @Override
          public int intVal(int doc) {
              int position = 0;

              String value = vals.strVal(doc);
              int index = values.indexOf(value);
              if (index != -1) {
                  position = index+1;
              }
              return position;
          }
      };
  }


    @Override
    public boolean equals(Object o) {
        if (this.getClass() != o.getClass()) return false;
        FixedBoostValueSource other = (FixedBoostValueSource) o;
        return field.equals(other.field) && fieldValueSource.equals(other.fieldValueSource) && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + field.hashCode() + fieldValueSource.hashCode() + values.hashCode();
    }

    public String name() {
        return "fixedBoost";
    }

    @Override
    public String description() {
        return name() + '(' + field + "," + values + ')';
    }
}

package org.opencommercesearch.api

import java.math

import com.fasterxml.jackson.databind.{DeserializationContext, JsonDeserializer}
import com.fasterxml.jackson.core.JsonParser

/**
 *
 */
class BigDecimalDeserializer extends JsonDeserializer[BigDecimal] {

  @Override
  def deserialize(jsonParser: JsonParser, deserializationContext: DeserializationContext) : BigDecimal = {
    new math.BigDecimal(jsonParser.getText)
  }


}

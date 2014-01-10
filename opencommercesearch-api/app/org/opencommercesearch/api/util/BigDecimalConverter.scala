package org.opencommercesearch.api.util

import com.wordnik.swagger.converter.{BaseConverter, ModelConverter, SwaggerSchemaConverter}
import com.wordnik.swagger.model.Model

/**
 * This converter prevents BigDecimal model properties from been rendered in the model schema.
 *
 * @author rmerizalde
 */
class BigDecimalConverter extends ModelConverter with BaseConverter {

  def read(cls: Class[_]): Option[Model] = None

  override def ignoredClasses: Set[String] = Set("scala.math.BigDecimal", "java.math.BigDecimal")

}

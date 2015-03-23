package org.opencommercesearch.api.models

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

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class QuantitySpec  extends Specification with Mockito {
  val delta = BigDecimal("0.0001")

  "Quantity" should {
    "convert between grams and pounds" in {
      val grams = new Quantity(BigDecimal("231"), Gram)
      val pounds = grams.to(Pound)

      pounds.amount should be closeTo(BigDecimal("0.509268") +/- delta)
      pounds.unit shouldEqual Pound

      val backToGrams = pounds.to(Gram)

      println(play.api.libs.json.Json.toJson(grams))
      println(play.api.libs.json.Json.toJson(pounds))
      println(play.api.libs.json.Json.toJson(backToGrams))

      backToGrams.amount should be closeTo(BigDecimal("231") +/- delta)
      backToGrams.unit shouldEqual Gram
    }
  }
}

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
import play.api.test.FakeApplication
import play.api.test.Helpers._

class ProductSpec extends Specification with Mockito {

  import org.opencommercesearch.api.models.Availability._

  "Product" should {

    "availability status should be None" in {
      running(FakeApplication()) {
        new Product().availabilityStatus must beEqualTo(None)
      }
    }

    "availability status should be InStock" in {
      running(FakeApplication()) {
        new Product(skus = createSkusWithStatus("OutOfStock", "Backorderable", "Preorderable","InStock","PermanentlyOutOfStock")).availabilityStatus must beEqualTo(Some(InStock))
      }
    }

    "availability status should be Backorderable" in {
      running(FakeApplication()) {
        new Product(skus = createSkusWithStatus("OutOfStock", "Backorderable", "Preorderable","PermanentlyOutOfStock")).availabilityStatus must beEqualTo(Some(Backorderable))
      }
    }

    "availability status should be Preorderable" in {
      running(FakeApplication()) {
        new Product(skus = createSkusWithStatus("OutOfStock", "OutOfStock", "Preorderable","PermanentlyOutOfStock")).availabilityStatus must beEqualTo(Some(Preorderable))
      }
    }

    "availability status should be OutOfStock" in {
      running(FakeApplication()) {
        new Product(skus = createSkusWithStatus("PermanentlyOutOfStock","PermanentlyOutOfStock", "OutOfStock", "OutOfStock")).availabilityStatus must beEqualTo(Some(OutOfStock))
      }
    }

    "availability status should be PermanentlyOutOfStock" in {
      running(FakeApplication()) {
        new Product(skus = createSkusWithStatus("PermanentlyOutOfStock","PermanentlyOutOfStock", "PermanentlyOutOfStock", "PermanentlyOutOfStock")).availabilityStatus must beEqualTo(Some(PermanentlyOutOfStock))
      }
    }
  }

  private def createSkusWithStatus(status: String*) = Some(status.map { s =>
    new Sku(availability = Some(new Availability(status = Some(s))))
  })
}

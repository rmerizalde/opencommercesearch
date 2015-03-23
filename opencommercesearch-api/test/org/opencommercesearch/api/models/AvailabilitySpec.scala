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

class AvailabilitySpec extends Specification with Mockito {
  import org.opencommercesearch.api.models.Availability._

  "Availability" should {

    "should succeed for a valid status/stockLevel combinations" in {
      new Availability(Some(InStock), Some(2))  must not(throwA[IllegalArgumentException])
      new Availability(Some(InStock), Some(2), Some(-1))  must not(throwA[IllegalArgumentException])
      new Availability(Some(InStock), Some(2), Some(0))  must not(throwA[IllegalArgumentException])
      new Availability(Some(InStock), Some(2), Some(2))  must not(throwA[IllegalArgumentException])
      new Availability(Some(OutOfStock), Some(0)) must not(throwA[IllegalArgumentException])
      new Availability(Some(PermanentlyOutOfStock), Some(0)) must not(throwA[IllegalArgumentException])
      new Availability(Some(Backorderable), Some(0), Some(-1)) must not(throwA[IllegalArgumentException])
      new Availability(Some(Backorderable), Some(0), Some(0)) must not(throwA[IllegalArgumentException])
      new Availability(Some(Backorderable), Some(0), Some(2)) must not(throwA[IllegalArgumentException])
    }

    "fail for a invalid status/stockLevel combinations" in {
      new Availability(Some(InStock), Some(0))  must throwA[IllegalArgumentException]
      new Availability(Some(OutOfStock), Some(2))  must throwA[IllegalArgumentException]
      new Availability(Some(PermanentlyOutOfStock), Some(3))  must throwA[IllegalArgumentException]
      new Availability(Some(Backorderable), Some(4))  must throwA[IllegalArgumentException]
      new Availability(Some(InStock), Some(-1))  must throwA[IllegalArgumentException]
      new Availability(Some(OutOfStock), Some(-2))  must throwA[IllegalArgumentException]
      new Availability(Some(PermanentlyOutOfStock), Some(-3))  must throwA[IllegalArgumentException]
      new Availability(Some(Backorderable), Some(-4))  must throwA[IllegalArgumentException]
    }

    "fail for a invalid status name" in {
      new Availability(Some("MyStatus"))  must throwA[IllegalArgumentException]
    }
  }
}

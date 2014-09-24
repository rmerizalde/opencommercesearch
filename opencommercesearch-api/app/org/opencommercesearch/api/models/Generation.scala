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

import com.fasterxml.jackson.annotation.JsonProperty
import play.api.libs.functional.syntax._
import org.opencommercesearch.api.Implicits._
import play.api.libs.json._

/**
 * Created by nkumar on 9/5/14.
 */

case class Generation (
  @JsonProperty("number") var number: Option[Int],
  @JsonProperty("master") var master: Option[Product]) {
}

object Generation {
  implicit val readsGenerationInfo : Reads[Generation] = (
     (__ \ "number").readNullable[Int] ~
     (__ \ "master").lazyReadNullable(Reads.of[Product])
  ) (Generation.apply _)

  implicit val writesGenerationInfo : Writes[Generation] = (
    (__ \ "number").writeNullable[Int] ~
    (__ \ "master").lazyWriteNullable(Writes.of[Product])
  ) (unlift(Generation.unapply))
}
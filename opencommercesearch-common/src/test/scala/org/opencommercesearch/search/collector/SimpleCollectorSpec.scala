package org.opencommercesearch.search.collector

/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements. See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import org.opencommercesearch.search.Element

/**
 * @author rmerizalde
 */
class SimpleCollectorSpec extends UnitSpec {

  "SimpleCollector" should "not add more elements when the collector capacity has been reached" in {
    val collector = new SimpleCollector[Element](2)
    val element = mock[Element]
    collector.add(element, "source") shouldBe true
    collector.add(element, "source") shouldBe true
    collector.add(element, "source") shouldBe false
    collector.canStop shouldBe true
  }

}

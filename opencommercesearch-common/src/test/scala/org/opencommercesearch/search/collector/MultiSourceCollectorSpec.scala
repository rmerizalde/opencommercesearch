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
class MultiSourceCollectorSpec extends UnitSpec {

  "MultiSourceCollector" should "return ignore an element when the source collector is not defined" in {
    val multiCollector = createMultiCollector(2)
    val element = mock[Element]

    multiCollector.add(element, "source1") shouldBe true
    multiCollector.add(element, "source1") shouldBe true
    multiCollector.add(element, "undefinedSource") shouldBe false
  }

  it should "have a capacity equal to the sum of all of its collectors' capacities" in {
    val expectedCapacity1 = 8
    val expectedCapacity2 = 2
    val multiCollector = createMultiCollector(expectedCapacity1, expectedCapacity2)

    multiCollector.capacity shouldBe (expectedCapacity1 + expectedCapacity2)
  }

  it should "have a size equal to the sum of all of its collectors' sizes" in {
    val multiCollector = createMultiCollector(8, 10)
    val element = mock[Element]
    val expectedSource1 = 6
    val expectedSource2 = 7

    for (i <- 1 to expectedSource1) multiCollector.add(element, "source1")
    for (i <- 1 to expectedSource2) multiCollector.add(element, "source2")
    for (i <- 1 to 2) multiCollector.add(element, "unknownSource")

    multiCollector.size shouldBe (expectedSource1 + expectedSource2)
  }

  it should "return all elements contain by its collectors" in {
    val multiCollector = createMultiCollector(2, 2)
    val element1 = mock[Element]
    val element2 = mock[Element]
    val element3 = mock[Element]
    val element4 = mock[Element]

    multiCollector.add(element1, "source1")
    multiCollector.add(element2, "source2")
    multiCollector.add(element3, "source2")
    multiCollector.add(element4, "unknownSource")

    val elements = multiCollector.elements()

    elements should have size multiCollector.size()
    elements should contain inOrder (element1, element2, element3)
    elements should not contain element4
  }

  it should "return all sources" in {
    val multiCollector = createMultiCollector(8, 10)
    val sources = multiCollector.sources

    sources should contain allOf ("source1", "source2")
  }

  it should "return all collectors" in {
    val collector1 = new SimpleCollector[Element]
    val collector2 = new SimpleCollector[Element]
    val multiCollector = new MultiSourceCollector[Element]

    multiCollector.add("source1", collector1)
    multiCollector.add("source2", collector2)

    val collectors = multiCollector.collectors
    collectors should contain allOf (collector1, collector2)
    multiCollector.collector("source1") shouldBe collector1
    multiCollector.collector("source2") shouldBe collector2
  }

  it should "return that is empty when all of its collectors are empty" in {
    val multiCollector = createMultiCollector(2, 2, 2, 2)

    multiCollector.isEmpty shouldBe true
  }

  it should "not return that is empty when at least one of its collector has one or more elements" in {
    val multiCollector = createMultiCollector(2, 2, 2, 2)
    val element = mock[Element]

    multiCollector.add(element, "source3")
    multiCollector.isEmpty shouldBe false
  }

  it should "not add a collector without a source" in {
    val multiCollector = new MultiSourceCollector[Element]
    val collector = new SimpleCollector[Element]

    multiCollector.add(null, collector) shouldBe false
    multiCollector.collectors shouldBe empty
  }

  it should "not add a source without a collector" in {
    val multiCollector = new MultiSourceCollector[Element]

    multiCollector.add("source", null) shouldBe false
    multiCollector.collectors shouldBe empty
  }

  it should "not contain itself as sourced collector" in {
    val multiCollector = new MultiSourceCollector[Element]

    multiCollector.add("source", multiCollector) shouldBe false
    multiCollector.collectors shouldBe empty
  }

  /**
   * Create a multi collector with the given capacities. The source
   * names of each collector are source1,...,sourceN where N is the
   * number of capacities
   *
   * @param capacity is the capacity for each collector
   * @return a new multi source collector
   * */
  private def createMultiCollector(capacity: Int*) = {
    val multiCollector = new MultiSourceCollector[Element]
    var i = 1

    for (c <- capacity) {
      val collector = new SimpleCollector[Element](c)
      multiCollector.add(s"source$i", collector)
      i += 1
    }
    multiCollector
  }
}

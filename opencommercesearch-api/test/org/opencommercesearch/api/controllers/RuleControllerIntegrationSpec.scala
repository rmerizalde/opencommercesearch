package org.opencommercesearch.api.controllers

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

import org.specs2.mutable._

import play.api.test._
import play.api.test.Helpers._

/**
 * An integration test will fire up a whole play application in a real (or headless) browser
 */
class RuleControllerIntegrationSpec extends Specification {
  
  "Rule Controller" should {
    
    "send 404 on a get /rules" in {
      running(TestServer(3333), HTMLUNIT) { browser =>

        val page = browser.goTo("http://localhost:3333/rules")

        page.pageSource must contain("Resource not found")
       
      }
    }
    
  }
  
}
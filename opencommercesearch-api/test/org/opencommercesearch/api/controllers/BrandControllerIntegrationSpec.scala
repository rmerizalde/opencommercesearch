package org.opencommercesearch.api.controllers

import org.specs2.mutable._

import play.api.test._
import play.api.test.Helpers._

/**
 * add your integration spec here.
 * An integration test will fire up a whole play application in a real (or headless) browser
 */
class BrandControllerIntegrationSpec extends Specification {
  
  "Brand Controller" should {
    
    "send 404 on a get /brands" in {
      running(TestServer(3333), HTMLUNIT) { browser =>

        val page = browser.goTo("http://localhost:3333/brands")

        page.pageSource must contain("Resource not found")
       
      }
    }
    
  }
  
}
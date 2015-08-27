package org.opencommercesearch.api

import play.api.test.Helpers._
import play.api.test.FakeApplication
import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import play.api.test.FakeRequest
import org.opencommercesearch.common.Context
import play.api.i18n.Lang
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ProductQuerySpec extends Specification with Mockito {

  private val lang = new Lang("en", "US")

  private def setupSortBy(sortParam: String, expectedSortField: String) = {
    implicit val context = Context(true, lang)
	implicit val request = FakeRequest(GET, "/test?sort="+sortParam)
	val productQuery = new ProductQuery("query", "site")
	productQuery.withSorting
	productQuery.getSortField() must beEqualTo(expectedSortField)
  }

  "ProductQuery" should {
    "Sorts by best seller desc" in {
     running(FakeApplication()) {
       setupSortBy("bestSeller", "sellRanksite desc" )
     }
    }
    "Sorts by best seller asc" in {
     running(FakeApplication()) {
       setupSortBy("bestSeller asc", "sellRanksite asc" )
     }
    }
  }

  "ProductQuery" should {
    "Sorts by revenue desc" in {
     running(FakeApplication()) {
       setupSortBy("revenue", "revenuesite desc" )
     }
    }
    "Sorts by revenue asc" in {
     running(FakeApplication()) {
       setupSortBy("revenue asc", "revenuesite asc" )
     }
    }
  }
}

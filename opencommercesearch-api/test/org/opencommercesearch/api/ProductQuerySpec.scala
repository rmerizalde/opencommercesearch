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
    implicit val request = FakeRequest(GET, "/test?sort="+sortParam)
    implicit val context = Context(true, lang)
    val productQuery = new ProductQuery("query", "site")
    productQuery.withSorting
    productQuery.getSortField() must beEqualTo(expectedSortField)
  }

  private def setupExcludeBackorder(site: String, shouldFilterBackorder: Boolean) = {
    implicit val context = Context(true, lang)
    implicit val request = FakeRequest(GET, "/test")
    val query = new ProductQuery("query", site)
    if (shouldFilterBackorder) {
      query.getFilterQueries.toList should contain ("(allowBackorderUS:false OR stockLevelUS:[1 TO *])")
    } else {
      query.getFilterQueries.toList should not contain ("allowBackorderUS:false OR stockLevelUS:[1 TO *]")
    }
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
    "Filter allowBackorder products only when the stock level is 0 when site has backorder excluded" in {
       running(new FakeApplication(additionalConfiguration = Map("sites.excludeBackorder" -> "siteWithExcludedBackorder"))) {
         setupExcludeBackorder("siteWithExcludedBackorder", true)
       }
    }
    "Not Filter allowBackorder products for a site that has backorder enabled" in {
       running(new FakeApplication(additionalConfiguration = Map("sites.excludeBackorder" -> "siteWithExcludedBackorder"))) {
         setupExcludeBackorder("siteAllowBackorder", false)
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

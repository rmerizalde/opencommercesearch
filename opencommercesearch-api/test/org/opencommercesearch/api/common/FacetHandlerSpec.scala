package org.opencommercesearch.api.common

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.mockito.Matchers
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.response.{FacetField, QueryResponse}
import org.apache.solr.common.util.NamedList
import org.opencommercesearch.api.service.Storage
import com.mongodb.WriteResult
import java.net.{URLEncoder, URLDecoder}
import org.opencommercesearch.api.models.{Facet, BreadCrumb}
import org.opencommercesearch.api.controllers.BaseSpec
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable
import scala.concurrent.Future
import java.util
import play.api.test._
import play.api.test.Helpers._

/**
 * Created by atguser on 2/11/14.
 */
class FacetHandlerSpec extends BaseSpec {

  var facetHandler : FacetHandler = null;
  var solrQuery : SolrQuery = mock[SolrQuery]
  var queryResponse: QueryResponse = mock[QueryResponse]
  var filterQueries: Array[FilterQuery] = Array.empty
  var facetData: Seq[NamedList[AnyRef]]  = Seq.empty
  var storage: Storage[WriteResult] = mock[Storage[WriteResult]]


  private def setup() : Unit = {

  }

  "FacetHandler" should {
    setup()
    
    "return no breadcrumbs when no facets are selected" in {
      var fq = Array.empty[FilterQuery]
      facetHandler = new FacetHandler(solrQuery, queryResponse, fq, facetData, storage)
      val response = facetHandler.getBreadCrumbs
      response.size mustEqual 0
    }

    "return two breadcrumbs when category facets are selected" in {
      var fq = FilterQuery.parseFilterQueries("category:2.mysite.category1.category2")
      facetHandler = new FacetHandler(solrQuery, queryResponse, fq, facetData, storage)
      val response = facetHandler.getBreadCrumbs
      validateBreadcrumb(response(0), "category", "category1", "")
      validateBreadcrumb(response(1), "category", "category2", "category:1.mysite.category1")
    }

    "return two breadcrumbs when category and brand facets are selected" in {
      var fq = FilterQuery.parseFilterQueries("category:1.mysite.category1|brand:myBrand")
      facetHandler = new FacetHandler(solrQuery, queryResponse, fq, facetData, storage)
      val response = facetHandler.getBreadCrumbs
      validateBreadcrumb(response(0), "category", "category1", "brand:myBrand")
      validateBreadcrumb(response(1), "brand", "myBrand", "category:1.mysite.category1")
    }

    "return three breadcrumbs when category, color and brand facets are selected" in {
      var fq = FilterQuery.parseFilterQueries("category:1.mysite.category1|brand:myBrand|colorFamily:blue")
      facetHandler = new FacetHandler(solrQuery, queryResponse, fq, facetData, storage)
      val response = facetHandler.getBreadCrumbs
      validateBreadcrumb(response(0), "category", "category1", "brand:myBrand|colorFamily:blue")
      validateBreadcrumb(response(1), "brand", "myBrand", "category:1.mysite.category1|colorFamily:blue")
      validateBreadcrumb(response(2), "colorFamily", "blue", "category:1.mysite.category1|brand:myBrand")
    }
    
    "return one breadcrumb when new arrivals is selected" in {
      running(FakeApplication()) {
        var fq = FilterQuery.parseFilterQueries("activationDate:[NOW-30DAY TO NOW]")
        facetHandler = new FacetHandler(solrQuery, queryResponse, fq, facetData, storage)
        val response = facetHandler.getBreadCrumbs
        validateBreadcrumb(response(0), "activationDate", "Last 30 days", "")
      }
    }

    "Remove facet filters when blacklisted" in {
      val facet = Facet.getInstance()
      facet.setId("facetId")
      facet.setBlackList(Seq("bluee"))
      storage.findFacets(any, any) returns Future.successful(Seq(facet))
      val facetFields: util.List[FacetField] = new util.LinkedList[FacetField]()
      val facetField = new FacetField("colorFamily")
      facetField.add("blue", 34)
      facetField.add("bluee", 1)
      facetFields.add(facetField)
      queryResponse.getFacetFields() returns facetFields

      val rawFacetData = new NamedList[AnyRef]()
      rawFacetData.add("name", "colorFamily")
      rawFacetData.add("id", "facetId")
      rawFacetData.add("fieldName", "colorFamily")
      rawFacetData.add("minBuckets", "1")

      facetHandler = new FacetHandler(solrQuery, queryResponse, Array.empty[FilterQuery], Seq(rawFacetData), storage)

      val facets = facetHandler.getFacets

      facets.size mustEqual 1
      facets(0).getName mustEqual "colorFamily"
      facets(0).filters.get.size mustEqual 1
      facets(0).filters.get(0).name.get mustEqual "blue"
    }
  }

  private def validateBreadcrumb(breadcrumb: BreadCrumb, fieldName: String, expression: String, path: String) = {
    breadcrumb.fieldName.get mustEqual fieldName
    breadcrumb.expression.get mustEqual expression
    breadcrumb.path.get mustEqual URLEncoder.encode(path, "UTF-8")
  }
}

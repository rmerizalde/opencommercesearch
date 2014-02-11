package org.opencommercesearch.api.common

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.mockito.Matchers
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.common.util.NamedList
import org.opencommercesearch.api.service.Storage
import com.mongodb.WriteResult
import java.net.{URLEncoder, URLDecoder}
import org.opencommercesearch.api.models.BreadCrumb
import org.opencommercesearch.api.controllers.BaseSpec
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable


/**
 * Created by atguser on 2/11/14.
 */
class FacetHandlerSpec  extends BaseSpec {

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
  }

  private def validateBreadcrumb(breadcrumb: BreadCrumb, fieldName: String, expression: String, path: String) = {
    breadcrumb.fieldName.get mustEqual fieldName
    breadcrumb.expression.get mustEqual expression
    breadcrumb.path.get mustEqual URLEncoder.encode(path, "UTF-8")
  }
}

package org.opencommercesearch.api.controllers

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import play.api.Logger
import play.api.libs.json.{JsArray, Json}

import scala.concurrent.Future
import scala.collection.convert.Wrappers.JIterableWrapper

import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.Util._
import org.opencommercesearch.api.models.Brand
import org.opencommercesearch.api.models.BrandList
import org.apache.solr.common.SolrDocument
import org.apache.solr.client.solrj.request.AsyncUpdateRequest
import org.apache.solr.client.solrj.SolrQuery


// @todo add support for other content types and default to json
object BrandController extends Controller with ContentPreview with FieldList with Pagination with ErrorHandling {

  def findById(version: Int, id: String, preview: Boolean) = Action { implicit request =>
    val query = withBrandCollection(withFields(new SolrQuery()), preview)

    query.setRequestHandler(RealTimeRequestHandler)
    query.add("id", id)

    Logger.debug("Query brand " + id)
    val future = solrServer.query(query).map( response => {
      val doc = response.getResponse.get("doc").asInstanceOf[SolrDocument]
      if (doc != null) {
        Logger.debug("Found brand " + id)
        Ok(Json.obj(
          "brand" -> Json.toJson(Brand.fromDocument(doc))))
      } else {
        Logger.debug("Brand " + id + " not found")
        NotFound(Json.obj(
          "message" -> s"Cannot find brand with id [$id]"
        ))
      }
    })

    Async {
      withErrorHandling(future, s"Cannot retrieve brand with id [$id]")
    }
  }

  def createOrUpdate(version: Int, id: String, preview: Boolean) = Action (parse.json) { request =>
    Json.fromJson[Brand](request.body).map { brand =>
      if (brand.logo.isEmpty || brand.logo.isEmpty) {
        BadRequest(Json.obj("message" -> "Missing required fields"))
      } else {
        val brandDoc = brand.toDocument

        brandDoc.setField("id", id)

        val update = new AsyncUpdateRequest()
        update.add(brandDoc)
        withBrandCollection(update, preview)

        val future: Future[Result] = update.process(solrServer).map( response => {
          Created.withHeaders((LOCATION, absoluteURL(routes.BrandController.findById(id), request)))
        })

        Async {
          withErrorHandling(future, s"Cannot store brand with id [$id]")
        }
      }
    }.recoverTotal {
      e => BadRequest(Json.obj(
        // @TODO figure out how to pull missing field from JsError
        "message" -> "Illegal brand fields"))
    }
  }

  def bulkCreateOrUpdate(version: Int, preview: Boolean) = Action (parse.json) { request =>
    Json.fromJson[BrandList](request.body).map { brandList =>
      val brands = brandList.brands

      if (brands.length > MaxUpdateBatchSize) {
        BadRequest(Json.obj(
          "message" -> s"Exceeded number of brands. Maximum is $MaxUpdateBatchSize"))
      } else if (hasMissingFields(brands)) {
        BadRequest(Json.obj(
          "message" -> "Missing required fields"))
      } else {
        val update = withBrandCollection(new AsyncUpdateRequest(), preview)
        update.add(brandList.toDocuments)

        val future: Future[Result] = update.process(solrServer).map( response => {
          Created(Json.obj(
            "locations" -> JsArray(
              brands map (b => Json.toJson(routes.BrandController.findById(b.id.get).url))
            )))
        })

        Async {
          withErrorHandling(future, s"Cannot store brands with ids [${brands map (_.id.get) mkString ","}]")
        }
      }
    }.recoverTotal {
      e => BadRequest(Json.obj(
        // @TODO figure out how to pull missing field from JsError
        "message" -> "Missing required fields"))
    }
  }

  def findSuggestions(version: Int, query: String, preview: Boolean) = Action { implicit request =>
    val solrQuery = withPagination(withBrandCollection(withFields(new SolrQuery(query)), preview))

    val future = solrServer.query(solrQuery).map( response => {
      val docs = response.getResults
      Ok(Json.obj(
        "metadata" -> Json.obj("found" -> docs.getNumFound),
        "suggestions" -> JIterableWrapper(docs).map(doc => doc.get("name").asInstanceOf[String])
      ))
    })

    Async {
      withErrorHandling(future, s"Cannot suggest brands for [$query]")
    }
  }

  /**
   * Helper method to check if any of the brand is missing a field.
   * @param brands is the list of brands to be validated
   * @return true of any of the brands is missing a single field
   */
  private def hasMissingFields(brands: List[Brand]) : Boolean = {
    var missingFields = false
    val brandIt = brands.iterator
    while (!missingFields && brandIt.hasNext) {
      val brand = brandIt.next()
      missingFields = brand.id.isEmpty ||
        brand.name.isEmpty ||
        brand.logo.isEmpty
    }
    missingFields
  }
}

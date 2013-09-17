package org.opencommercesearch.api.controllers

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import play.api.Logger
import play.api.libs.json.{JsError, Json}

import scala.collection.JavaConversions.asScalaBuffer

import java.util

import org.opencommercesearch.api.models._
import org.apache.solr.client.solrj.SolrQuery
import org.codehaus.jackson.map.ObjectMapper

import org.opencommercesearch.api.Global._
import scala.collection.convert.Wrappers.JIterableWrapper
import scala.collection.convert.Wrappers.JListWrapper
import scala.concurrent.Future
import org.apache.solr.client.solrj.request.AsyncUpdateRequest


object ProductController extends Controller with ContentPreview with FieldList with Pagination with ErrorHandling {

  val mapper = new ObjectMapper()

  def search(version: Int, q: String, preview: Boolean) = Action { implicit request =>
    val query = withProductCollection(withFields(withPagination(new SolrQuery(q))), preview)

    query.set("group", true)
    query.set("group.ngroups", true)
    query.set("group.limit", 50)
    query.set("group.field", "productId")
    query.set("group.facet", false)
    //query.set("fl", "id,image")

    Logger.debug("Searching for " + q)

    val future = solrServer.query(query).map( response => {

      val groupResponse = response.getGroupResponse

      if (groupResponse != null) {
        val commands = groupResponse.getValues

        if (commands.size > 0) {
          val command = groupResponse.getValues.get(0)

          if ("productId".equals(command.getName)) {
            if (command.getNGroups > 0) {
              val allProducts = new util.ArrayList[Product]
              for (group <- JIterableWrapper(command.getValues)) {
                val productBeans = solrServer.binder.getBeans(classOf[SolrSku], group.getResult)
                if (productBeans.size() > 0) {
                  var title:String = null

                  if (productBeans.get(0).title.isDefined) {
                    title = productBeans.get(0).title.get
                  }

                  val productBeanSeq = asScalaBuffer(productBeans).map(b => {b.title = None; b})
                  val p = new Product()
                  p.setId(group.getGroupValue)
                  p.setTitle(title)
                  //p.setSkus(productBeanSeq)
                  allProducts.add(p)
                }
              }
              Ok(Json.obj(
                "metadata" -> Json.obj("found" -> command.getNGroups.intValue()),
                "products" -> Json.arr(
                  JListWrapper(allProducts) map (Json.toJson(_))
                )))
            } else {
              Logger.debug("Unexpected response found for query  " + q)
              InternalServerError(Json.obj(
                "message" -> "Unable to execute query"
              ))
            }
          } else {
            Logger.debug("Unexpected response found for query  " + q)
            InternalServerError(Json.obj(
              "message" -> "Unable to execute query"
            ))
          }
        } else {
          Logger.debug("Unexpected response found for query  " + q)
          InternalServerError(Json.obj(
            "message" -> "Unable to execute query"
          ))
        }
      } else {
        Logger.debug("Unexpected response found for query  " + q)
        InternalServerError(Json.obj(
          "message" -> "Unable to execute query"
        ))
      }
    })

    Async {
      withErrorHandling(future, s"Cannot search for [$q]")
    }
  }

  def bulkCreateOrUpdate(version: Int, preview: Boolean) = Action(parse.json(maxLength = 1024 * 2000)) { implicit request =>
    Json.fromJson[ProductList](request.body).map { productList =>
      val products = productList.products
      if (products.size > MaxUpdateProductBatchSize) {
        BadRequest(Json.obj(
          "message" -> s"Exceeded number of products. Maximum is $MaxUpdateProductBatchSize"))
      } else {
        try {
          val update = withProductCollection(new AsyncUpdateRequest(), preview)
          val docs = productList.toDocuments
          update.add(docs)

          val future: Future[Result] = update.process(solrServer).map( response => {
            Created
          })

          Async {
            withErrorHandling(future, s"Cannot store products with ids [${products map (_.id.get) mkString ","}]")
          }
        } catch {
          case e: IllegalArgumentException => {
            Logger.error(e.getMessage)
            BadRequest(Json.obj(
              "message" -> e.getMessage
            ))
          }
        }
      }
    }.recoverTotal {
      case e: JsError => {
        BadRequest(Json.obj(
          // @TODO figure out how to pull missing field from JsError
          "message" -> "Missing required fields"))
      }

    }
  }

  def deleteByTimestamp(version: Int = 1, feedTimestamp: Long, preview: Boolean) = Action { implicit request =>
    val update = withProductCollection(new AsyncUpdateRequest(), preview)
    update.deleteByQuery("-indexStamp:" + feedTimestamp)

    val future: Future[Result] = update.process(solrServer).map( response => {
      NoContent
    })

    Async {
      withErrorHandling(future, s"Cannot delete product before feed timestamp [$feedTimestamp]")
    }
  }

  def deleteById(version: Int = 1, id: String, preview: Boolean) = Action { implicit request =>
    val update = withProductCollection(new AsyncUpdateRequest(), preview)
    update.deleteByQuery("productId:" + id)

    val future: Future[Result] = update.process(solrServer).map( response => {
      NoContent
    })

    Async {
      withErrorHandling(future, s"Cannot delete product [$id  ]")
    }
  }
}

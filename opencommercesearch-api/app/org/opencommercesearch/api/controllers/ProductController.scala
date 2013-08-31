package org.opencommercesearch.api.controllers

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import play.api.mvc.Controller
import play.api.Logger
import play.api.libs.json.{JsArray, Json}

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.convert.Wrappers.{JListWrapper, JIterableWrapper}

import java.util

import org.opencommercesearch.api.models.{Product, Sku}
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.beans.DocumentObjectBinder
import org.codehaus.jackson.map.ObjectMapper

import org.opencommercesearch.api.Global._


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
                val productBeans = solrServer.binder.getBeans(classOf[Sku], group.getResult)
                if (productBeans.size() > 0) {
                  var title:Option[String] = None

                  if (productBeans.get(0).title.isDefined) {
                    title = productBeans.get(0).title
                  }

                  val productBeanSeq = asScalaBuffer(productBeans).map(b => {b.title = None; b})
                  allProducts.add(new Product(Some(group.getGroupValue), title, Some(productBeanSeq)))
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

      //val products = JListWrapper(response.getBeans(classOf[Product]))


      /*if (docs != null && docs.getNumFound > 0) {
        Logger.debug("Found " + docs.getNumFound + " document for query " + q)
        Ok(Json.obj(
          "metadata" -> Json.obj("found" -> docs.getNumFound),
          "products" -> JsArray(
            // @todo figure out how to implements Writes[Product] using jackson annotations
            // writing a json string to parse it back to json is not the long term solution
            products map (p => Json.parse(mapper.writeValueAsString(p))))
        ))
      } else {
        Logger.debug("No results found for query  " + q)
        Ok(Json.obj(
          "metadata" -> Json.obj("found" -> docs.getNumFound)
        ))
      }
    })*/

    Async {
      withErrorHandling(future, s"Cannot search for [$q]")
    }
  }
}

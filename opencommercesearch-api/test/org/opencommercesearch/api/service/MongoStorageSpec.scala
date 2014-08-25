package org.opencommercesearch.api.service

import play.api.i18n.Lang
import play.api.test.FakeApplication
import play.api.test.Helpers._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

import java.util.Date

import org.opencommercesearch.api.models._
import org.opencommercesearch.common.Context

import org.jongo.Jongo
import org.jongo.marshall.jackson.JacksonMapper
import org.junit.runner.RunWith
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.github.fakemongo.Fongo

@RunWith(classOf[JUnitRunner])
class MongoStorageSpec extends Specification with Mockito  {

  private val lang = new Lang("en", "US")

  var fongo : Fongo = null
  var storage: MongoStorage = null
  
  private def setup() : Unit = {
    fongo = new Fongo("Mocked Mongo server")
    storage = new MongoStorage(fongo.getMongo)
	val jongo = new Jongo(fongo.getMongo.getDB("testdb"), new JacksonMapper.Builder().registerModule(new DefaultScalaModule).build())
	storage.setJongo(jongo)
  }

   "MongoStorage" should {
    setup()

    "save a brand and then find it" in {
     running(FakeApplication()) {
	     
	     implicit val context = Context(preview = true, lang)
	     
	     val sites = Some(Seq("1"))
	     val brand = new Brand(Some("id"), Some("name"), Some("logo"), Some("url"), sites) 
	     Await.result(storage.saveBrand(brand), Duration.Inf)
	     
	     val response: Future[Brand] = storage.findBrand("id",Seq("*"))
	     val savedBrand = Await.result(response, Duration.Inf)
	     
	     savedBrand.getId must beEqualTo("id")
	     savedBrand.getName must beEqualTo("name")
	     savedBrand.logo.get must beEqualTo("logo")
	     savedBrand.url.get must beEqualTo("url")
	     savedBrand.sites.get must contain("1")
     }
    }
    
    "save a category and then find it" in {
     running(FakeApplication()) {
	     
	     implicit val context = Context(preview = true, lang)

	     val category = new Category(Some("id"), Some("name"), Some("alias"), Some("urlToken"), Some(false),
	    		 					 Some(Seq()), Some(Seq("site1")), Some(Seq("token1")), Some(Seq()), Some(Seq()))
	     Await.result(storage.saveCategory(category), Duration.Inf)
	     
	     val response: Future[Category] = storage.findCategory("id",Seq("*"))
	     val savedCat = Await.result(response, Duration.Inf)
	     
	     savedCat.getId must beEqualTo("id")
	     savedCat.getName must beEqualTo("name")
	     savedCat.getUrl  must beEqualTo("urlToken")
	     savedCat.isRuleBased.get must beEqualTo(false)
	     savedCat.hierarchyTokens must beEqualTo(None)
	     savedCat.getSites must contain("site1")
	     
     }
    }
    
    "find a toos product toos in the US and a stock product in CA" in {
     running(FakeApplication()) {
	     
	     implicit val context = Context(preview = true, lang)
	     
	     val sites = Some(Seq("1"))
	     
	     val brand = new Brand(Some("id"), Some("name"), Some("logo"), Some("url"), sites) 
	     
	     val category = new Category(Some("id"), Some("name"), Some("alias"), Some("urlToken"), Some(false),
	    		 					 Some(Seq()), Some(Seq("site1")), Some(Seq("token1")), Some(Seq()), Some(Seq()))
	     
	     val usCountry = new Country(code = Some("US"), url = Some("url"), availability = Some(new Availability(status = Some(Availability.OutOfStock), stockLevel = Some(0), backorderLevel = Some(0))))
       val caCountry = new Country(code = Some("CA"), url = Some("url"), availability = Some(new Availability(status = Some(Availability.InStock), stockLevel = Some(2), backorderLevel = Some(0))))

	     val sku = new Sku(
	    	 id = Some("id1"), season = Some("season"), year = Some("year"), countries = Some(Seq(usCountry)), isPastSeason = Some(false),
	       title = Some("title"), isRetail = Some(true), isCloseout = Some(false), isOutlet = Some(false), catalogs = Some(Seq("bcs")),
         onSale = Some(false), availability = usCountry.availability, url = Some("url"), allowBackorder = Some(false))

       val skuInStock = new Sku(
         id = Some("id"), season = Some("season"), year = Some("year"), countries = Some(Seq(caCountry)), isPastSeason = Some(false),
         title = Some("title"), isRetail = Some(true), isCloseout = Some(false), isOutlet = Some(false), catalogs = Some(Seq("bcs")),
         onSale = Some(false), availability = caCountry.availability, url = Some("url"), allowBackorder = Some(true))

	     val productToos = new Product(
	       id = Some("id"), title = Some("title"), description = Some("description"), shortDescription = Some("shortDesc"), brand = Some(brand), gender = Some("gender"),
         sizingChart = Some("sizeChart"), listRank = Some(1), availabilityStatus = Some(Availability.InStock),
         categories = Some(Seq(category)), skus = Some(Seq(sku, skuInStock)), activationDate = Some(new Date()), isPackage = Some(false))

	     Await.result(storage.saveCategory(category), Duration.Inf)
	     Await.result(storage.saveBrand(brand), Duration.Inf)
	     Await.result(storage.saveProduct(productToos), Duration.Inf)

	     val response: Future[Iterable[Product]] = storage.findProducts(Seq(("id",null)), null, lang.country, Seq("*"), minimumFields = false)
	     val savedProdIterable = Await.result(response, Duration.Inf)
	     
	     val headProduct = savedProdIterable.head
	     headProduct.getId must beEqualTo("id")
	     savedProdIterable.size must beEqualTo(1)
	     
     }
    }
   }
}
package org.opencommercesearch.api.service

import play.api.test.Helpers._
import play.api.test.FakeApplication
import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import com.mongodb.MongoClient
import org.opencommercesearch.common.Context
import play.api.i18n.Lang
import org.jongo.Jongo
import org.jongo.marshall.jackson.JacksonMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.opencommercesearch.api.models.Brand
import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration.Duration
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import com.github.fakemongo.Fongo
import org.opencommercesearch.api.models.Category
import org.opencommercesearch.api.models.Sku
import org.opencommercesearch.api.models.Country
import org.opencommercesearch.api.models.Product
import java.util.Date

@RunWith(classOf[JUnitRunner])
class MongoStorageSpec extends Specification with Mockito  {

  private val lang = new Lang("en", "US")

  var fongo : Fongo = null
  var storage: MongoStorage = null
  
  private def setup() : Unit = {
    fongo = new Fongo("Mocked Mongo server")
    storage = new MongoStorage(fongo.getMongo())
	val jongo = new Jongo(fongo.getMongo().getDB("testdb"), new JacksonMapper.Builder().registerModule(new DefaultScalaModule).build())
	storage.setJongo(jongo)
  }
  
  private def tearDown(): Unit = {
  }

   "MongoStorage" should {
    setup()

    "save a brand and then find it" in {
     running(FakeApplication()) {
	     
	     implicit val context = Context(true, lang)
	     
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
	     
	     implicit val context = Context(true, lang)
	     
	     val sites = Some(Seq("1"))
	     val category = new Category(Some("id"),Some("name"),Some("urlToken"),Some(false),
	    		 					 Some(Seq()),Some(Seq("site1")),Some(Seq("token1")), Some(Seq()),Some(Seq())) 
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
	     
	     implicit val context = Context(true, lang)
	     
	     val sites = Some(Seq("1"))
	     
	     val brand = new Brand(Some("id"), Some("name"), Some("logo"), Some("url"), sites) 
	     
	     val category = new Category(Some("id"),Some("name"),Some("urlToken"),Some(false),
	    		 					 Some(Seq()),Some(Seq("site1")),Some(Seq("token1")), Some(Seq()),Some(Seq()))
	     
	     val usCountry = new Country(Some("US"), None, None, None, None, Some(0), Some("url"), Some(false))
       val caCountry = new Country(Some("CA"), None, None, None, None, Some(0), Some("url"), Some(false))

	     val sku = new Sku(
	    		 Some("id"), Some("season"), Some("year"), None, Some(Seq(usCountry)), Some(false), None,
	    		 Some("title"), Some(true), Some(false), Some(false), None, Some(Seq("bcs")),
	    		 None, None, None, Some(false), Some(0), Some("url"), Some(true), None)

       val skuInStock = new Sku(
         Some("id"), Some("season"), Some("year"), None, Some(Seq(caCountry)), Some(false), None,
         Some("title"), Some(true), Some(false), Some(false), None, Some(Seq("bcs")),
         None, None, None, Some(false), Some(10), Some("url"), Some(true),None)

	     val productToos = new Product(
	        Some("id"), Some("title"), Some("description"), Some("shortDesc"), Some(brand), Some("gender"),
			    Some("sizeChart"), None, None, None, None, Some(1), None, None,
			    Some(true), Some(Seq(category)), Some(Seq(sku, skuInStock)), Some(new Date()), Some(false), None)

	     Await.result(storage.saveCategory(category), Duration.Inf)
	     Await.result(storage.saveBrand(brand), Duration.Inf)
	     Await.result(storage.saveProduct(productToos), Duration.Inf)

	     val response: Future[Iterable[Product]] = storage.findProducts(Seq(("id",null)), null, lang.country, Seq("*"), false)
	     val savedProdIterable = Await.result(response, Duration.Inf)
	     
	     val headProduct = savedProdIterable.head
	     headProduct.getId must beEqualTo("id")
	     headProduct.isOutOfStock.get must beEqualTo(true)
	     
	     savedProdIterable.size must beEqualTo(1)
	     
     }
    }
   }
}
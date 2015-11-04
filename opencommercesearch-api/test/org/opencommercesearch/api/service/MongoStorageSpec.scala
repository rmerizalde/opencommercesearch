package org.opencommercesearch.api.service

/*
* Licensed to OpenCommerceSearch under one
* or more contributor license agreements. See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership. OpenCommerceSearch licenses this
* file to you under the Apache License, Version 2.0 (the
* "License") you may not use this file except in compliance
* with the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*/

import org.junit.runner.RunWith
import org.opencommercesearch.api.models.{Availability, Product, Sku}
import org.opencommercesearch.api.service.MongoStorage._
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.mockito.Matchers
import org.specs2.runner.JUnitRunner
import play.api.test.Helpers._
import play.api.test.FakeApplication
import reactivemongo.api.DefaultDB
import reactivemongo.bson.BSONDocument
import reactivemongo.api.collections.default.BSONCollection
import reactivemongo.api.collections.GenericQueryBuilder
import reactivemongo.api.Cursor
import reactivemongo.bson.BSONDocumentReader
import reactivemongo.bson.BSONDocumentWriter
import scala.concurrent.duration._
import scala.concurrent.{Await,Future}
import org.specs2.mutable.Before


class MongoStorageSpec extends Specification with Mockito {

  var product = new Product
  var storage: MongoStorage = null
  var db = mock[DefaultDB]
  var productsCollection = mock[BSONCollection]
  var mockCursor = mock[Cursor[Product]]
  var genericQueryBuilder = mock[GenericQueryBuilder[BSONDocument, BSONDocumentReader, BSONDocumentWriter]]

  trait MongoStore extends Before {
    def before = {

      db[BSONCollection]("products") returns productsCollection

      productsCollection.find(any, Matchers.eq(DefaultEmptyProjection))(any, any) returns genericQueryBuilder
      productsCollection.find(Matchers.eq(BSONDocument()))(any) returns genericQueryBuilder

      genericQueryBuilder.cursor[Product](any, any) returns mockCursor

      mockCursor.collect[Seq]() returns Future(Seq(product))
      mockCursor.collect[Seq](any, any)(any, any) returns Future(Seq(product))

      storage = new MongoStorage(db)
    }
  }

  sequential

  "MongoStorage.findRawProduct" should {
    "find valid products" in new MongoStore {
        val futureProducts = storage.findRawProducts(Seq("productId1")) map { products =>
          val currentProd : Product = products.head
          currentProd must beEqualTo(product)
          products.size must beEqualTo(1)
        }
        Await.result(futureProducts, Duration.Inf)
    }

    "find empty products" in new MongoStore {
        val futureProducts = storage.findRawProducts(Seq.empty[String]) map { products =>
          products.size must beEqualTo(0)
        }
        Await.result(futureProducts, Duration.Inf)
    }
  }

  "MongoStorage.projectProduct" should {
    "add sku availability status and country code if product's availabilityStatus is requested and no sku data is requested" in new MongoStore {
      val fields = Seq("id", "availabilityStatus")
      val projection = storage.projectProduct("mysite", fields, singleSku = false, Seq.empty[String])
      val expectedProject = BSONDocument(
        "id" -> Include,
        "availabilityStatus" -> Include,
        ProjectedSkuCountryCode,
        ProjectedSkuCatalog,
        ProjectedSkuAvailabilityStatus
      )

      BSONDocument.pretty(projection) shouldEqual BSONDocument.pretty(expectedProject)
    }

    "add sku availability status and country code if product's availabilityStatus is requested and the needed sku availability data is not requested" in new MongoStore {
      val fields = Seq("id", "availabilityStatus", "skus.listPrice", "skus.salePrice")
      val projection = storage.projectProduct("mysite", fields, singleSku = false, Seq.empty[String])
      val expectedProject = BSONDocument(
        "id" -> MongoStorage.Include,
        "availabilityStatus" -> Include,
        "skus.countries.listPrice" -> Include,
        "skus.countries.salePrice" -> Include,
        ProjectedSkuCountryCode,
        ProjectedSkuCatalog,
        ProjectedSkuAvailabilityStatus,
        ProjectedSkuCountryCatalogPrices
      )

      BSONDocument.pretty(projection) shouldEqual BSONDocument.pretty(expectedProject)
    }

    "add sku availability status and country code if product's availabilityStatus is requested and sku availability status is not requested" in new MongoStore {
      val fields = Seq("id", "availabilityStatus", "skus.listPrice", "skus.salePrice", "sku.availability.stockLevel")
      val projection = storage.projectProduct("mysite", fields, singleSku = false, Seq.empty[String])
      val expectedProject = BSONDocument(
        "id" -> MongoStorage.Include,
        "availabilityStatus" -> Include,
        "skus.countries.listPrice" -> Include,
        "skus.countries.salePrice" -> Include,
        "sku.availability.stockLevel" -> Include,
        ProjectedSkuCountryCode,
        ProjectedSkuCatalog,
        ProjectedSkuAvailabilityStatus,
        ProjectedSkuCountryCatalogPrices
      )

      BSONDocument.pretty(projection) shouldEqual BSONDocument.pretty(expectedProject)
    }
  }

  "MongoStorage.clearSkuAvailability" should {
    "remove sku availability status if product's availabilityStatus is requested and no sku data is requested" in new MongoStore {
      running(FakeApplication()) {
        val skus = Seq(new Sku(availability = Some(new Availability(status = Some("InStock")))))
        val product = new Product(id = Some("PRD0001"), availabilityStatus = Some("InStock"), skus = Some(skus))
        val fields = Seq("id", "availabilityStatus")
        val expectedProduct = product.copy()

        expectedProduct.skus = None
        storage.clearSkuAvailability(product, fields) shouldEqual expectedProduct
      }
    }
    "remove availability status and country code if product's availabilityStatus is requested and the needed sku availability data is not requested" in new MongoStore {
      running(FakeApplication()) {
        val sku = new Sku(availability = Some(new Availability(status = Some("InStock"))), listPrice = Some(24.99), salePrice = Some(19.99))
        val skus = Seq(sku)
        val product = new Product(id = Some("PRD0001"), availabilityStatus = Some("InStock"), skus = Some(skus))
        val fields = Seq("id", "availabilityStatus", "skus.listPrice", "skus.salePrice")
        val expectedProduct = product.copy()

        sku.availability = None
        storage.clearSkuAvailability(product, fields) shouldEqual expectedProduct
      }
    }
    "remove availability status and country code if product's availabilityStatus is requested and sku availability status is not requested" in new MongoStore {
      running(FakeApplication()) {
        val availability = new Availability(status = Some("InStock"), stockLevel = Some(20))
        val sku = new Sku(availability = Some(availability), listPrice = Some(24.99), salePrice = Some(19.99))
        val skus = Seq(sku)
        val product = new Product(id = Some("PRD0001"), availabilityStatus = Some("InStock"), skus = Some(skus))
        val fields = Seq("id", "availabilityStatus", "skus.listPrice", "skus.salePrice", "sku.availability.stockLevel")
        val expectedProduct = product.copy()

        availability.status = None
        storage.clearSkuAvailability(product, fields) shouldEqual expectedProduct
      }
    }
  }
}

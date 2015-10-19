package org.opencommercesearch.api.models

/*
* Licensed to OpenCommerceSearch under one
* or more contributor license agreements. See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership. OpenCommerceSearch licenses this
* file to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
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

import org.apache.solr.common.SolrInputDocument
import org.opencommercesearch.api.service.CategoryService
import org.opencommercesearch.common.Context
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import play.api.test.Helpers._
import play.api.libs.json._

class ProductSpec extends Specification with Mockito {

  import org.opencommercesearch.api.models.Availability._

  "Product" should {

    "json deserialize" in {
      running(FakeApplication()) {
          val text = """{
          "id": "PAT00FN", "title": "Down Sweater Jacket - Men's",
          "brand": { "name": "Patagonia" },
          "customerReviews": { "count": 25, "average": 4.5 },
          "availabilityStatus": "InStock",
          "categories": [],
          "tags": ["tag1", "tag2"],
          "skus": [ { "id": "PAT00FN-TURRD-L", "image": {
            "url": "/images/items/medium/PAT/PAT00FN/TURRD.jpg"
          },
          "isPastSeason": false, "title": "Turkish Red, L", "isRetail": true, "isCloseout": false,
          "isOutlet": false, "listPrice": 229, "salePrice": 137.4, "discountPercent": 40,
          "onSale": true, "url": "/patagonia-down-sweater-mens",
          "availability": { "status": "InStock", "stockLevel": 6  } } ] }
          """
          val json = Json.parse(text)
          val productOpt = json.asOpt[Product]
          productOpt.isDefined must beTrue
          val product = productOpt.get
          product.id.isDefined must beTrue
          product.id.get must beEqualTo("PAT00FN")
          product.title.isDefined must beTrue
          product.title.get must beEqualTo("Down Sweater Jacket - Men's")
          product.availabilityStatus.isDefined must beTrue
          product.availabilityStatus.get must beEqualTo("InStock")
          product.skus.isDefined must beTrue
          product.skus.get.size must beEqualTo(1) // Rest of the Sku validation is delegated to Sku Test
          product.tags.isDefined must beTrue
          product.tags.get.size must beEqualTo(2)
          product.tags.get.toList(0) must beEqualTo("tag1")
          product.tags.get.toList(1) must beEqualTo("tag2")
      }
    }

    "json serialize" in {
      running(FakeApplication()) {
        val tags = "tag1" :: "tag2" :: List.empty[String]
        val product = Product(id = Option("ID123"), title=Option("MyTitle"), tags = Option(tags.toSeq))
        val json = Json.toJson(product)
        (json \ "id").asOpt[String].isDefined must beTrue
        (json \ "id").as[String] must beEqualTo("ID123")
        (json \ "title").asOpt[String].isDefined must beTrue
        (json \ "title").as[String] must beEqualTo("MyTitle")
        (json \ "tags").asOpt[Seq[String]].isDefined must beTrue
        (json \ "tags").as[Seq[String]].toList(0) must beEqualTo("tag1")
        (json \ "tags").as[Seq[String]].toList(1) must beEqualTo("tag2")
      }
    }

    "availability status should be None" in {
      running(FakeApplication()) {
        new Product().availabilityStatus must beEqualTo(None)
      }
    }

    "availability status should be InStock" in {
      running(FakeApplication()) {
        new Product(skus = createSkusWithStatus("OutOfStock", "Backorderable", "Preorderable","InStock","PermanentlyOutOfStock")).availabilityStatus must beEqualTo(Some(InStock))
      }
    }

    "availability status should be Backorderable" in {
      running(FakeApplication()) {
        new Product(skus = createSkusWithStatus("OutOfStock", "Backorderable", "Preorderable","PermanentlyOutOfStock")).availabilityStatus must beEqualTo(Some(Backorderable))
      }
    }

    "availability status should be Preorderable" in {
      running(FakeApplication()) {
        new Product(skus = createSkusWithStatus("OutOfStock", "OutOfStock", "Preorderable","PermanentlyOutOfStock")).availabilityStatus must beEqualTo(Some(Preorderable))
      }
    }

    "availability status should be OutOfStock" in {
      running(FakeApplication()) {
        new Product(skus = createSkusWithStatus("PermanentlyOutOfStock","PermanentlyOutOfStock", "OutOfStock", "OutOfStock")).availabilityStatus must beEqualTo(Some(OutOfStock))
      }
    }

    "availability status should be PermanentlyOutOfStock" in {
      running(FakeApplication()) {
        new Product(skus = createSkusWithStatus("PermanentlyOutOfStock","PermanentlyOutOfStock", "PermanentlyOutOfStock", "PermanentlyOutOfStock")).availabilityStatus must beEqualTo(Some(PermanentlyOutOfStock))
      }
    }

    "should generate a document field per site specific field" in {
      running(FakeApplication()) {
        val countryUS = Country(
          code = Some("US"),
          listPrice = Some(BigDecimal(199.5)),
          salePrice = Some(BigDecimal(199.5)),
          discountPercent = Some(0),
          onSale = Some(false),
          defaultPrice = Some(Price(
            listPrice = Some(BigDecimal(199.5)),
            salePrice = Some(BigDecimal(199.5)),
            discountPercent = Some(0),
            onSale = Some(false)
          )),
          catalogPrices = Some(Map(
            "site2" -> Price(
                listPrice = Some(BigDecimal(199.5)),
                salePrice = Some(BigDecimal(149.5)),
                discountPercent = Some(25),
                onSale = Some(true)
            )
          ))
        )
        val countryCA = Country(
          code = Some("CA"),
          listPrice = Some(BigDecimal(198.5)),
          salePrice = Some(BigDecimal(198.5)),
          discountPercent = Some(0),
          onSale = Some(false),
          defaultPrice = Some(Price(
            listPrice = Some(BigDecimal(198.5)),
            salePrice = Some(BigDecimal(198.5)),
            discountPercent = Some(0),
            onSale = Some(false)
          )),
          catalogPrices = Some(Map(
            "site2" -> Price(
                listPrice = Some(BigDecimal(198.5)),
                salePrice = Some(BigDecimal(128.5)),
                discountPercent = Some(35),
                onSale = Some(true)
            )
          ))
        )
        val sku = Sku(
          id = Some("PRD0001-S"),
          image = Some(Image(title = Some("Image Title"), url = Some("image.url"))),
          isRetail = Some(true),
          isCloseout = Some(false),
          countries = Some(Seq(countryUS, countryCA)),
          catalogs = Some(Seq("site1", "site2"))
        )
        val product = new Product(
          id = Some("PRD0001"),
          title = Some("Product Title"),
          brand = Some(Brand(id = Some("brandId"), name = Some("Brand Name"))),
          skus = Some(Seq(sku)),
          listRank = Some(1)
        )

        val productList = ProductList(Seq(product), System.currentTimeMillis())
        val service = mock[CategoryService]
        implicit val context = mock[Context]
        val doc = productList.toDocuments(service).get(0)

        def validateSiteSpecificField(name: String, country: String, defaultValue: Any, site1Value: Any, site2Value: Any) = {
          doc.getFieldValue(name + country) shouldEqual defaultValue
          doc.getFieldValue(name + country + "site1") shouldEqual site1Value
          doc.getFieldValue(name + country + "site2") shouldEqual site2Value
        }

        doc.getFieldValue("listPriceUS") shouldEqual 199.5
        validateSiteSpecificField("salePrice", "US", 199.5, 199.5, 149.5)
        validateSiteSpecificField("discountPercent", "US", 0, 0, 25)
        validateSiteSpecificField("onsale", "US", false, false, true)


        doc.getFieldValue("listPriceCA") shouldEqual 198.5
        validateSiteSpecificField("salePrice", "CA", 198.5, 198.5, 128.5)
        validateSiteSpecificField("discountPercent", "CA", 0, 0, 35)
        validateSiteSpecificField("onsale", "CA", false, false, true)
      }


    }
  }

  private def createSkusWithStatus(status: String*) = Some(status.map { s =>
    new Sku(availability = Some(new Availability(status = Some(s))))
  })
}

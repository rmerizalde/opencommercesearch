package org.opencommercesearch.api.service

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

import org.opencommercesearch.api.models._
import reactivemongo.core.commands.LastError
import scala.concurrent.Future

/**
 * Represents the storage for catalog object including products, brand and categories
 *
 * @author rmerizalde
 */
trait Storage[T] {

  /**
   * Return the product count
   */
  def countProducts() : Future[Long]

  /**
   * Finds the products with the given ids. Ids is a sequence of tuples. The first element of the tuple is the product id.
   * The second element is an optional sku id. If the sku id is different than null, only the skus that matches the id
   * is returned. Otherwise, returns all skus for a products
   * @param ids is a sequence of product id/sku id tuples
   * @param country is the country to filter by
   * @param fields is the list of fields to return
   * @param minimumFields indicates if we return minimum fields
   * @return
   */
  def findProducts(ids: Seq[(String, String)], country: String, fields: Seq[String], minimumFields:Boolean) : Future[Iterable[Product]]

  /**
   * Finds the products with the given ids. Ids is a sequence of tuples. The first element of the tuple is the product id.
   * The second element is an optional sku id. If the sku id is different than null, only the skus that matches the id
   * is returned. Otherwise, returns all skus for a products
   * @param ids is a sequence of product id/sku id tuples
   * @param site is the site to search skus
   * @param country is the country to filter by
   * @param fields is the list of fields to return
   * @param minimumFields indicates if we return minimum fields
   * @return
   */
  def findProducts(ids: Seq[(String, String)], site:String, country: String, fields: Seq[String], minimumFields:Boolean) : Future[Iterable[Product]]

  /**
   * Finds the product with the given id. Optionally, takes a list of fields to return
   * @param id is the product id
   * @param country is the country to filter by
   * @param fields is the list of fields to return
   * @return
   */
  def findProduct(id: String, country: String, fields: Seq[String]) : Future[Product]

  /**
   * Finds the product with the given id for the given site. If the product exists but none of its skus are assigned to
   * site null is return
   *
   * @param id is the product id
   * @param site is the site id
   * @param country is the country to filter by
   * @param fields is the list of fields to return
   * @return
   */
  def findProduct(id: String, site: String, country: String, fields: Seq[String]) : Future[Product]

  /**
   * Saves the given list of products. Returns the result of the last write
   * @param product is one or more products to store
   * @return the results of writing the last product
   */
  def saveProduct(product: Product*) : Future[T]

  /**
   * Delete the product with the given id. Returns the result of the deleting the product
   * @param id is of the product to delete
   * @return the result of deleting the product
   */
  def deleteProduct(id: String) : Future[T]

    /**
   * Saves the given list of categories. Returns the result of the last write
   * @param category is one or more categories to store
   * @return the results of writing the last category
   */
  def saveCategory(category: Category*) : Future[T]
  
  /**
   * Finds the category with the given id. Optionally, takes a list of fields to return.
   * If childCategories or parentCategories are specified in the fields parameter, then the
   * category will have the corresponding nested categories populated with whatever other fields
   * you specified in the fields list, not only having the category id. 
   * The method returns only one level depth of nested categories information
   * 
   * For example:   
   *  fields=id,name,seoUrlToken,childCategories
   *  -->
   *  category = { id: ...,  name:..., seoUrlToken:...,  
   *     childCategories: [  {id: ...,  name:..., seoUrlToken:...} ]
   *  }
   *  
   *  * Note: for nested category properties, only id, name, seoUrlToken and catalogs will be populated
   *  
   * @param id is the category id
   * @param fields is the list of fields to return
   * @return
   */
  def findCategory(id: String, fields: Seq[String]) : Future[Category]

  /**
   * Find all existing categories from storage.
   * @param fields List of fields that should be retrieved.
   * @return Collection of category objects populated with storage data.
   */
  def findAllCategories(fields: Seq[String]) : Future[Iterable[Category]]

  /**
   * Find all given category ids.
   * @param ids A collection of category ids to look for.
   * @param fields List of fields that should be retrieved.
   * @return Collection of category objects populated with storage data, based on the given ids.
   */
  def findCategories(ids: Iterable[String], fields: Seq[String]) : Future[Iterable[Category]]
  
  /**
   * Finds the brand with the given id. Optionally, takes a list of fields to return
   * @param id is the brand id
   * @param fields is the list of fields to return
   * @return
   */
  def findBrand(id: String, fields: Seq[String]) : Future[Brand]
  
  /**
   * Finds the brands that match the ids in the provided list. Optionally, takes a list of fields to return
   * @param ids is the list of brand ids
   * @param fields is the list of fields to return
   * @return the list of brands
   */
  def findBrands(ids: Iterable[String], fields: Seq[String]) : Future[Iterable[Brand]]

  /**
   * Finds the brands that match the names in the provided list. Optionally, takes a list of fields to return
   * @param names is the list of brand names
   * @param fields is the list of fields to return
   * @return the list of brands
   *
   * @todo implement brands sorted by name
   */
  def findBrandsByName(names: Iterable[String], fields: Seq[String]) : Future[Iterable[Brand]]
  
   /**
   * Saves the given list of brands. Returns the result of the last write
   * @param brand is one or more brands to store
   * @return the results of writing the last brand
   */
  def saveBrand(brand: Brand*) : Future[T]

  /**
   * Saves the given list of facets. Returns the result of the last write
   * @param facet is one or more facets to store
   * @return the results of writing the last facet
   */
  def saveFacet(facet: Facet*) : Future[T]

  /**
   * Saves the given list of rules. Returns the result of the last write
   * @param rule is one or more rules to store
   * @return the results of writing the last rule
   */
  def saveRule(rule: Rule*) : Future[T]

  /**
   * Finds a facet with the given id. Optionally, takes a list of fields to return
   * @param id is the facet id
   * @param fields is the list of fields to return
   * @return Facet from storage if found
   */
  def findFacet(id: String, fields: Seq[String]) : Future[Facet]

  /**
   * Finds the facets with the given ids. Optionally, takes a list of fields to return
   * @param ids is the list of facet ids
   * @param fields is the list of fields to return
   * @return List of facets from storage
   */
  def findFacets(ids: Iterable[String], fields: Seq[String]) : Future[Iterable[Facet]]

  /**
   * Finds a rule with the given id. Optionally, takes a list of fields to return
   * @param id is the rule id
   * @param fields is the list of fields to return
   * @return Rule from storage if found
   */
  def findRule(id: String, fields: Seq[String]) : Future[Rule]

  /**
   * Finds the rules with the given ids. Optionally, takes a list of fields to return
   * @param ids is the list of rule ids
   * @param fields is the list of fields to return
   * @return List of rules from storage
   */
  def findRules(ids: Iterable[String], fields: Seq[String]) : Future[Iterable[Rule]]
  
    /**
   * Finds the content for products with the given ids. Ids is a sequence of tuples. The first element of the tuple is the product id.
   * @param ids is a sequence of product id tuples
   * @param site is the site to search contents
   * @return
   */
  def findContent(ids: Seq[(String, String)], site:String) : Future[Iterable[ProductContent]]

  /**
   * Finds the content for products with the given ids. Ids is a sequence of tuples. The first element of the tuple is the product id.
   * @param ids is a sequence of product id tuples
   * @return
   */
  def findContent(ids: Seq[(String, String)]) : Future[Iterable[ProductContent]]
  
  /**
   * Saves the given list of product contents. Returns the result of the last write
   * @param product is one or more products to store
   * @return the results of writing the last product content
   */
  def saveProductContent(feedTimestamp: Long, site: String, contents: ProductContent*) : Future[LastError]
  
  /**
   * Delete the content with the given id. Returns the result of deleting the content
   * @param id is of the content to delete
   * @return the result of deleting the content
   */
  def deleteContent(id: String, feedTimestamp: Long, site: String) : Future[LastError]
}

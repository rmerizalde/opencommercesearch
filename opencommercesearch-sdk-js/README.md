# OpenCommerceSearch Javascript SDK

## Description
JS version of the ProductApi SDK to be used on the server and (eventually) client.

---
## Common Tasks

#### Install
```sh
npm install opencommercesearch-ocs-sdk
```

#### Test
Run style and unit tests once.
```sh
npm test
```

or

```sh
grunt test
```

#### Develop
Continuosly run style and unit tests while watching for changes.
```sh
grunt develop
```

---

## Basic Usage
All API methods make async requests and return promises which support .then() .done() and .fail() methods. Read more about promises [here](https://www.npmjs.com/package/q).
```js
var productApiService = require('opencommercesearch-sdk-js');

productApiService.config({
  host: 'api.domain.com'
});

productApiService
  .searchProducts({
    q: 'a search term',
    site: 'your_site'
  })
  .then(function(data) {
    // do something
  });
```
---

## Utility Methods

#### **.config(options)**
Configures the service, best used at application startup.
><table>
>  <thead><tr><th>Property</th><th>Type</th><th>Description</th></tr></thead>
>  <tbody>
>    <tr><td>host</td><td>String</td><td>the host to use for all requests, should only be set once during server start</td></tr>
>    <tr><td>preview</td><td>boolean</td><td>whether or not to set the </td></tr>
>    <tr><td>debug</td><td>boolean</td><td>exposes additional helper methods and logging for debug purposes</td></tr>
>  </tbody>
></table>

#### **.getConfig()**
Returns the current configuration settings.


---
## API Methods

Available methods are grouped below by the type of item returned (product, category, etc). All methods accept a single options object requiring at least a **site**, and usually **one or more** additional properties. Additionally, there are several **optional** properties that can be passed to most endpoints:

*  **fields** --- all endpoints have default fields that can be overridden with a comma separated list of field names
    * dot notation is supported for nested fields ```brand.name```
    * during development ```fields: '*'``` can be used to return all fields but is not recommended for production
    * *always request the least amount of data required*
* **metadata** --- (string) used to specify what metadata is returned and is useful for reducing the size of the request
    * for a product search request where facets are not required, using ```metadata: found```  can significantly reduce the size of the response
* **limit** --- (int) number of items to return, search/browse endpoints have a max limit of 40
* **offset** --- (int) number of items to offset, used for pagination on search/browse endpoints
* **preview** --- (boolean) whether or not to return results from the preview environment
* **outlet** --- (boolean) whether or not to return products/categories in outlet
* **filterQueries** --- (string) filters (facets) to apply to search/browse endpoints, values can be found in metadata.facets.filters.filterQueries



### Products

#### **.findProducts(options)**
Returns the specified product(s). Multiple products can be requested at once by passing a comma-separated string or array of productIds.
><table>
>  <thead><tr><th>Property</th><th>Type</th><th>Description</th></tr></thead>
>  <tbody>
>    <tr><td>productId</td><td>String|Array</td><td>one or more productIds</td></tr>
>    <tr><td>site</td><td>String</td><td>site code</td></tr>
>  </tbody>
></table>

---
#### **.searchProducts(options)**
Returns all products that match the search query.
><table>
>  <thead><tr><th>Property</th><th>Type</th><th>Description</th></tr></thead>
>  <tbody>
>    <tr><td>q</td><td>String</td><td>a search query</td></tr>
>    <tr><td>site</td><td>String</td><td>site code</td></tr>
>  </tbody>
></table>

---
#### **.browseCategory(options)**
Returns all products belonging to a particular category.
><table>
>  <thead><tr><th>Property</th><th>Type</th><th>Description</th></tr></thead>
>  <tbody>
>    <tr><td>categoryId</td><td>String</td><td>a single categoryId</td></tr>
>    <tr><td>site</td><td>String</td><td>site code</td></tr>
>  </tbody>
></table>

---
#### **.browseBrand(options)**
Returns all products belonging to a particular brand.
><table>
>  <thead><tr><th>Property</th><th>Type</th><th>Description</th></tr></thead>
>  <tbody>
>    <tr><td>brandId</td><td>String</td><td>a single brandId</td></tr>
>    <tr><td>site</td><td>String</td><td>site code</td></tr>
>  </tbody>
></table>

---
#### **.browseBrandCategory(options)**
Returns all products belonging to a particular brand and category.
><table>
>  <thead><tr><th>Property</th><th>Type</th><th>Description</th></tr></thead>
>  <tbody>
>    <tr><td>brandId</td><td>String</td><td>a single brandId</td></tr>
>    <tr><td>categoryId</td><td>String</td><td>a single categoryId</td></tr>
>    <tr><td>site</td><td>String</td><td>site code</td></tr>
>  </tbody>
></table>

---
#### **.findSimilarProducts(options)**
Returns products similar to the product specified.
><table>
>  <thead><tr><th>Property</th><th>Type</th><th>Description</th></tr></thead>
>  <tbody>
>    <tr><td>productId</td><td>String</td><td>a single productId</td></tr>
>    <tr><td>site</td><td>String</td><td>site code</td></tr>
>  </tbody>
></table>

---
#### **.findProductGenerations(options)**
Returns all generations of the product.
><table>
>  <thead><tr><th>Property</th><th>Type</th><th>Description</th></tr></thead>
>  <tbody>
>    <tr><td>productId</td><td>String</td><td>a single productId</td></tr>
>    <tr><td>site</td><td>String</td><td>site code</td></tr>
>  </tbody>
></table>

---
#### **.suggestProducts(options)**
Returns products with brand and titles matching the query.
><table>
>  <thead><tr><th>Property</th><th>Type</th><th>Description</th></tr></thead>
>  <tbody>
>    <tr><td>q</td><td>String</td><td>a search query</td></tr>
>    <tr><td>site</td><td>String</td><td>site code</td></tr>
>  </tbody>
></table>

---

### Categories

#### **.findCategory(options)**
Returns the specified category.
><table>
>  <thead><tr><th>Property</th><th>Type</th><th>Description</th></tr></thead>
>  <tbody>
>    <tr><td>categoryId</td><td>String</td><td>a single categoryId</td></tr>
>    <tr><td>site</td><td>String</td><td>site code</td></tr>
>    <tr><td>maxLevels</td><td>Int</td><td>(optional) the number of taxonomy levels, defaults to 1</em></td></tr>
>    <tr><td>maxChildren</td><td>Int</td><td>(optional) the number childCategories to return per taxonomy level, defaults to all</em></td></tr>
>  </tbody>
></table>

---
#### **.categoryTaxonomy(options)**
Returns the top level category taxonomy for the specified site. Use ```maxLevels: -1``` to return the entire taxonomy (only as needed).
><table>
>  <thead><tr><th>Property</th><th>Type</th><th>Description</th></tr></thead>
>  <tbody>
>    <tr><td>site</td><td>String</td><td>site code</td></tr>
>    <tr><td>maxLevels</td><td>Int</td><td>(optional) the number of taxonomy levels, defaults to 1</em></td></tr>
>    <tr><td>maxChildren</td><td>Int</td><td>(optional) the number childCategories to return per taxonomy level, defaults to all</em></td></tr>
>  </tbody>
></table>

---
#### **.findBrandCategories(options)**
Returns all categories for the specified brand.
><table>
>  <thead><tr><th>Property</th><th>Type</th><th>Description</th></tr></thead>
>  <tbody>
>    <tr><td>brandId</td><td>String</td><td>a single brandId</td></tr>
>    <tr><td>site</td><td>String</td><td>site code</td></tr>
>  </tbody>
></table>

---
#### **.suggestCategories(options)**
Returns categories with titles that substring match the query.
><table>
>  <thead><tr><th>Property</th><th>Type</th><th>Description</th></tr></thead>
>  <tbody>
>    <tr><td>q</td><td>String</td><td>a search query</td></tr>
>    <tr><td>site</td><td>String</td><td>site code</td></tr>
>  </tbody>
></table>


---
### Brands

#### **.findBrands(options)**
Returns the brand specified.
><table>
>  <thead><tr><th>Property</th><th>Type</th><th>Description</th></tr></thead>
>  <tbody>
>    <tr><td>brandId</td><td>String</td><td>a single brandId</td></tr>
>    <tr><td>site</td><td>String</td><td>site code</td></tr>
>  </tbody>
></table>

---
#### **.allBrands(options)**
Returns all brands for the specified site.
><table>
>  <thead><tr><th>Property</th><th>Type</th><th>Description</th></tr></thead>
>  <tbody>
>    <tr><td>site</td><td>String</td><td>site code</td></tr>
>  </tbody>
></table>

---
#### **.suggestBrands(options)**
Returns all brands with names that substring match the query.
><table>
>  <thead><tr><th>Property</th><th>Type</th><th>Description</th></tr></thead>
>  <tbody>
>    <tr><td>q</td><td>String</td><td>a search query</td></tr>
>    <tr><td>site</td><td>String</td><td>site code</td></tr>
>  </tbody>
></table>

#### **.findCategoryBrands(options)**
Returns all brands with at least one product in the specified category.
><table>
>  <thead><tr><th>Property</th><th>Type</th><th>Description</th></tr></thead>
>  <tbody>
>    <tr><td>categoryId</td><td>String</td><td>a single categoryId</td></tr>
>    <tr><td>site</td><td>String</td><td>site code</td></tr>
>  </tbody>
></table>

---
### Suggestions

#### **.suggestAll(options)**
Returns suggestions for queries, products, brands, and categories that substring match the query.
><table>
>  <thead><tr><th>Property</th><th>Type</th><th>Description</th></tr></thead>
>  <tbody>
>    <tr><td>q</td><td>String</td><td>a search query</td></tr>
>    <tr><td>site</td><td>String</td><td>site code</td></tr>
>  </tbody>
></table>

---
### Queries

#### **.suggestQueries(options)**
Returns suggestions for user search term queries that substring match query.
><table>
>  <thead><tr><th>Property</th><th>Type</th><th>Description</th></tr></thead>
>  <tbody>
>    <tr><td>q</td><td>String</td><td>a search query</td></tr>
>    <tr><td>site</td><td>String</td><td>site code</td></tr>
>  </tbody>
></table>

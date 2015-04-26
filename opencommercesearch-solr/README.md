Contains custom components used by OpenCommerceSerch API.

Boost Function
==============

The boost function allows adding dynamic boosts to search requests. The component requires a boostId which is sent with
the search request in order to fetch the boost values for the current page. The component will retrieve the boosts using
the OCS Boost API. Here are ways on how to use the function:

 sort=boost(productId) desc

 // query hook
 term OR _val_:boost(productId)

 // additive boost (short hand for query hook)
 bf=boost(productId)

 // multiplicative boost
 boost=SUM(1, boost(productId))

 The application can choose to boost by product or sku. To boost by sku simply change productId to id. Note, the application
 need to write the sku boosts accordingly.

 For more information on the Boost API see BoostController @ https://github.com/rmerizalde/opencommercesearch/tree/develop/opencommercesearch-api


Fixed Boost Function
====================

The fixed boost allows pinning search results at the top n positions overriding the search engine ranking. Here is how
to use the function:

sort=fixedBoost(productId, prodX, prodY, prodZ) asc

If the products are included in the search results prodX will show in position 1, prodY in position 2 and prodZ in position 3.

Rule Manager Component
======================

The rule manager component takes care of amending the search request based on the user defined rules. For more information
see:

* https://www.youtube.com/watch?v=GewiWmyImz0
* https://github.com/rmerizalde/opencommercesearch/tree/develop/opencommercesearch-atg-pub

Group Collapse Component
========================

The current implementation of OCS returns the top sku for each product on the results. This component allows generating
product summaries across all product' skus. For example, min and max list price, max discount percent, etc. This component
is used to build the productSummary metadata in the following request:

http://api.backcountry.com/v1/products?q=jackets&site=bcs&metadata=time,productSummary

Expand All Component
====================

The expand all component is a copy of the ExpandComponent included in Solr out of the box. The only difference is that
it includes the all the grouped documents in the expanded section. The default implementation doesn't include the document
returned as part of the main results.




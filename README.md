ecommercesearch
===============

Installation

* Copy jar to lib
* Add jar to lib & config path in META-INF
* Map the repositories for in the topology files
* Add preview and producton repos to atg/dynamo/service/AssetResolver.properties
* Add this to atg/remote/commerce/browse/MerchandisingBrowseHierarchy.xml

 <browse-item id="search" label="Search" is-root="true">
      <list-definition show-count-on-header="false" id="searchFolderChildContent" set-site-context-on-drilldown="false" retriever="query" allow-drilldown="false" show-count-on-children="false" show-header="conditionally" allow-load="true" child-type="/org/ecommercesearch/atg/repository/SearchRepository:synonym">
        <retriever-parameter name="query" value="ALL"/>
      </list-definition>
 </browse-item>

* Add this to the versionedRepositories property in atg/epub/version/VersionManagerService.properties

 SearchRepository=/org/ecommercesearch/atg/repository/SearchRepository

* Add the repository to the initialRepositories in atg/registry/ContentRepositories.properties


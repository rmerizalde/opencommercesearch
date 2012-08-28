ecommercesearch
===============

Installation

* Copy jar to lib
* Add jar to lib & config path in META-INF
* Map the repositories for in the topology files
* Add preview and producton repos to atg/dynamo/service/AssetResolver.properties
* Add this atg/dynamo/service/idspaces.xml
  <id-space name="synonym" prefix="syn" seed="1" batch-size="1000"/>
* Add this to atg/remote/commerce/browse/MerchandisingBrowseHierarchy.xml

 <browse-item reference-id="search"/>

 <browse-item id="search" label="Search" is-root="true">
      <list-definition show-count-on-header="false" id="searchFolderChildContent" set-site-context-on-drilldown="false" retriever="query" allow-drilldown="false" show-count-on-children="false" show-header="conditionally" allow-load="true" child-type="/org/ecommercesearch/atg/repository/SearchRepository_ver:synonym">
        <retriever-parameter name="query" value="ALL"/>
      </list-definition>
 </browse-item>

* Add this to atg/remote/commerce/toolbar/MerchandisingToolbar.xml

  <operation-menu id="browseSearchOperationMenu">
    <toolbar-scope pane-id="browse" asset-area="searchTool"/>
    <operation-menu-item id="edit" divider="false" submenu="false"/>
    <operation-menu-item id="duplicate" divider="false" submenu="false"/>
    <operation-menu-item id="delete" divider="false" submenu="false"/>
    <operation-menu-item divider="true" submenu="false"/>
    <operation-menu-item id="addToProject" divider="false" submenu="false"/>
    <operation-menu-item id="export" divider="false" submenu="false"/>
  </operation-menu> 

* Add this to the versionedRepositories property in atg/epub/version/VersionManagerService.properties

 SearchRepository=/org/ecommercesearch/atg/repository/SearchRepository_ver

* Add the repository to the initialRepositories in atg/registry/ContentRepositories.properties


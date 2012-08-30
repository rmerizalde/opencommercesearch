ecommercesearch
===============

Installation

* Copy jar to lib
* Add jar to lib & config path in META-INF
* Map the repositories for in the topology files

    <repository-mapping>
      <source-repository>/org/ecommercesearch/repository/SearchRepository</source-repository>
      <destination-repository>/org/ecommercesearch/repository/SearchRepository_preview</destination-repository>
    </repository-mapping>

    <repository-mapping>
      <source-repository>/org/ecommercesearch/repository/SearchRepository</source-repository>
      <destination-repository>/org/ecommercesearch/repository/SearchRepository_production</destination-repository>
    </repository-mapping>

* Add preview and producton repos to atg/dynamo/service/AssetResolver.properties

      /org/ecommercesearch/repository/SearchRepository_preview,\
      /org/ecommercesearch/repository/SearchRepository_production

* Add this atg/dynamo/service/idspaces.xml
  <id-space name="synonym" prefix="syn" seed="1" batch-size="1000"/>
* Add this to atg/remote/commerce/browse/MerchandisingBrowseHierarchy.xml

Add this to home at beggining <browse-item reference-id="search"/>

  <browse-item id="search" label="Search" is-root="true">
    <list-definition show-count-on-header="false" id="searchFolderChildContent" set-site-context-on-drilldown="false" retriever="query" allow-drilldown="false" show-count-on-children="false" show-header="conditionally" allow-load="true" child-type="/org/ecommercesearch/repository/SearchRepository:synonym">
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

 SearchRepository=/org/ecommercesearch/repository/SearchRepository

* Add the repository to the initialRepositories in atg/registry/ContentRepositories.properties

 /org/ecommercesearch/repository/SearchRepository

* Configure the deployment agents atg/epub/DeploymentAgent.properties to add the IndexingDeploymentListener

deploymentEventListeners+=/org/ecommercesearch/deployment/IndexingDeploymentListener


Create the /org/ecommercesearch/repository/SearchRepository.properties under Publishing with this content

    $class=atg.adapter.version.VersionRepository

    versionManager=/atg/epub/version/VersionManagerService
    versionItemsByDefault=true
    dataSource=/atg/dynamo/service/jdbc/JTDataSource

NOTE:

After starting the bcc BCC, map the SearchRepository to each target SearchRepository


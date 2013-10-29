commercesearch
===============

Installation

* Copy the opencommercesearch-solr jar to the lib directory under the lib directory in Sorl home directory.

By default, the configurtion files use the Synonym Expanding Query Parser. Hence you need to deploy the jar hon-lucene-synonyms-1.2.2-solr-4.1.0.jar in the same lib directory

* Add opencommercesearch-pub jar under the lib in the Publishing module and update the config path in META-INF. For example:

ATG-Class-Path: lib/classes lib/classes.jar lib/opencommercesearch-pub-0.2-SNAPSHOT.jar
ATG-Config-Path: config lib/opencommercesearch-pub-0.2-SNAPSHOT.jar

* Add opencommercesearch-common jar under the lib in the Store module and update the config path in META-INF. For example:

ATG-Class-Path: lib/hotfix/p13311143_1001_v1_lib.jar
  lib/classes lib/classes.jar lib/resources.jar
  ...
  lib/opencommercesearch/opencommercesearch-common-0.2-SNAPSHOT.jar lib/opencommercesearch/opencommercesearch-solr-0.2-SNAPSHOT.jar
  lib/solr/solr-solrj-4.5.0.jar lib/solr/solr-core-4.5.0.jar lib/solr/zookeeper-3.4.5.jar lib/solr/spatial4j-0.3.jar
  lib/lucene/lucene-core-4.5.0.jar lib/lucene/lucene-analyzers-common-4.5.0.jar
  lib/lucene/lucene-queries-4.5.0.jar lib/lucene/lucene-queryparser-4.5.0.jar lib/lucene/lucene-grouping-4.5.0.jar
  lib/lucene/lucene-suggest-4.5.0.jar lib/lucene/lucene-highlighter-4.5.0.jar lib/lucene/lucene-memory-4.5.0.jar
  lib/lucene/lucene-misc-4.5.0.jar lib/lucene/lucene-analyzers-phonetic-4.5.0.jar
  lib/lucene/hon-lucene-synonyms-1.3.1-solr-4.3.0.jar 
  ...
ATG-Config-Path: lib/opencommercesearch/opencommercesearch-common-0.2-SNAPSHOT.jar config

NOTE: you may need to add more dependencies to the class path.

* Map the repositories for in the topology files

    <repository-mapping>
      <source-repository>/org/commercesearch/repository/SearchRepository</source-repository>
      <destination-repository>/org/commercesearch/repository/SearchRepository_preview</destination-repository>
    </repository-mapping>

    <repository-mapping>
      <source-repository>/org/commercesearch/repository/SearchRepository</source-repository>
      <destination-repository>/org/commercesearch/repository/SearchRepository_production</destination-repository>
    </repository-mapping>

* Add this atg/dynamo/service/idspaces.xml
  <id-space name="synonym" prefix="syn" seed="1" batch-size="1000"/>
  <id-space name="synonymList" prefix="synList" seed="1" batch-size="1000"/>
  <id-space name="rule" prefix="rule" seed="1" batch-size="1000"/>

* By default, OpenCommerceSearch configures the deployment agents atg/epub/DeploymentAgent.properties to add the IndexingDeploymentListener on all instances where opencommercesearch-common is deployed. If you have multiple instances for a given environment you may want to disable on some of them. Otherwise, the search server will re-index rules on all instances.

* Deploy a SearchServer configuration files to use different collection/server. Typical ATG deployments have two environments: preview & public. By default, the SearchServer.properties file included in the common jar points to the preview instance:

catalogCollection=catalogPreview
rulesCollection=rulePreview

For public facing instance, you need to create a SearchServer.properties to point the server to the public collections:

catalogCollection=catalogPublic
rulesCollection=rulePublic    

NOTE:

After starting the bcc BCC, map the SearchRepository to each target SearchRepository. Make changss live!!


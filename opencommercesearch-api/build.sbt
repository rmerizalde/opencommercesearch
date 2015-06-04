import java.util.Properties

import de.johoop.jacoco4sbt.HTMLReport
import play.PlayImport.PlayKeys._
import sbt.KeyRanks._

import scala.io.Source

lazy val root = (project in file(".")).enablePlugins(PlayScala)
val versions = SettingKey[Properties]("versions", "Module & dependency versions", APlusSetting)

versions := {
  val properties = new Properties()
  properties.load(Source.fromFile("../version.properties").reader())
  properties
}

name := "opencommercesearch-api"

version := versions.value.getProperty("ocs")

scalaVersion := versions.value.getProperty("scala")

scalacOptions ++= Seq("-feature")

resolvers ++= Seq(
  Resolver.file("Local repo", file(System.getProperty("user.home") + "/.ivy2/local"))(Resolver.ivyStylePatterns),
  "oss-releases" at "http://oss.jfrog.org/artifactory/libs-release/",
  "oss-snapshots" at "http://oss.jfrog.org/artifactory/libs-snapshot/"
)


libraryDependencies ++= Seq(
  cache, filters,
  "org.opencommercesearch" %% "play-solrj" % versions.value.getProperty("play-solrj") changing(),
  "org.opencommercesearch" %% "opencommercesearch-common" % versions.value.getProperty("ocs") changing(),
  "com.typesafe.play.plugins" %% "play-statsd" % versions.value.getProperty("play-statsd"),
  "com.wordnik" %% "swagger-play2" % versions.value.getProperty("swagger-play2"),
  "org.reactivemongo" %% "reactivemongo" % versions.value.getProperty("reactivemongo"),
  "org.apache.solr" % "solr-core" % versions.value.getProperty("solr") excludeAll(
    ExclusionRule(organization = "log4j"),
    ExclusionRule(organization = "org.slf4j", name = "slf4j-log4j12")
  ),
  "org.mockito" % "mockito-all" % versions.value.getProperty("mockito-all") % "test",
  "org.hamcrest" % "hamcrest-all" % versions.value.getProperty("hamcrest-all") % "test",
  "com.github.fakemongo" % "fongo" % "1.6.0" % "test"
)

organization := "org.opencommercesearch"

publishMavenStyle := true

publishTo <<= version { version: String =>
  val baseUrl = "http://oss.jfrog.org/artifactory/"
  val (name, url) = if (version.contains("-SNAPSHOT"))
    ("oss-snapshots-pub", baseUrl + "oss-snapshot-local/")
  else
    ("oss-pub", baseUrl + "oss-release-local/")
  Some(Resolver.url(name, new URL(url))(Resolver.mavenStylePatterns))
}

packagedArtifacts += ((artifact in playPackageAssets).value -> playPackageAssets.value)

// Jacoco

jacoco.settings

parallelExecution in jacoco.Config := false

jacoco.reportFormats in jacoco.Config := Seq(HTMLReport("utf-8"))

jacoco.excludes in jacoco.Config := Seq("views*", "*Routes*", "*controllers*routes*", "*controllers*Reverse*", "*controllers*javascript*", "*controller*ref*", "*Controller")


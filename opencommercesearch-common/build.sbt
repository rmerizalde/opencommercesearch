import java.util.Properties

import sbt.KeyRanks._
import sbt.Keys._

import scala.io.Source

val versions = SettingKey[Properties]("versions", "Module & dependency versions", APlusSetting)

versions := {
  val properties = new Properties()
  properties.load(Source.fromFile("../versions.sbt").reader())
  properties
}

name := "opencommercesearch-common"

version := versions.value.getProperty("ocs")

scalaVersion := versions.value.getProperty("scala")

libraryDependencies ++= Seq(
  "org.opencommercesearch" %% "play-solrj" % versions.value.getProperty("play-solrj") % "provided",
  "org.scalatest" %% "scalatest" % versions.value.getProperty("scalatest") % "test",
  "org.mockito" % "mockito-all" % versions.value.getProperty("mockito-all") % "test"
)

resolvers ++= Seq(
  "oss-releases" at "http://oss.jfrog.org/artifactory/libs-release/",
  "oss-snapshots" at "http://oss.jfrog.org/artifactory/libs-snapshot/",
  "Typesafe Snapshot Repository" at "http://repo.typesafe.com/typesafe/snapshots/",
  "Typesafe Release Repository" at "http://repo.typesafe.com/typesafe/releases/"
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


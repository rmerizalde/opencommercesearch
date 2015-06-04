import java.net.URL
import java.util.Properties

import de.johoop.jacoco4sbt.HTMLReport
import de.johoop.jacoco4sbt.JacocoPlugin.jacoco
import sbt.KeyRanks._
import sbt.Keys._
import sbt.{Resolver, SettingKey}

import scala.io.Source

val versions = SettingKey[Properties]("versions", "Module & dependency versions", APlusSetting)

versions := {
  val properties = new Properties()
  properties.load(Source.fromFile("../version.properties").reader())
  properties
}

name := "opencommercesearch-atg-common"

version := versions.value.getProperty("ocs")

scalaVersion := versions.value.getProperty("scala")

libraryDependencies ++= Seq(
  "org.apache.solr" % "solr-test-framework" % versions.value.getProperty("solr") % "test" excludeAll ExclusionRule(organization = "org.apache.lucene", name = "lucene-core"),
  "org.apache.lucene" % "lucene-core" % versions.value.getProperty("solr") % "test",
  "org.apache.solr" % "solr-core" % versions.value.getProperty("solr"),
  "org.opencommercesearch" % "opencommercesearch-solr" % versions.value.getProperty("ocs") changing(),
  "org.opencommercesearch" % "opencommercesearch-sdk-java" % versions.value.getProperty("ocs") changing() excludeAll ExclusionRule(organization = "ch.qos.logback"),
  "log4j" % "log4j" % versions.value.getProperty("log4j"),
  "org.slf4j" % "slf4j-jdk14" % "1.7.12",
  "commons-codec" % "commons-codec" % versions.value.getProperty("commons-codec") % "provided",
  "commons-lang" % "commons-lang" % versions.value.getProperty("commons-lang") % "provided",
  "com.fasterxml.jackson.core" % "jackson-annotations" % versions.value.getProperty("jackson"),
  "com.fasterxml.jackson.core" % "jackson-core" % versions.value.getProperty("jackson"),
  "com.fasterxml.jackson.core" % "jackson-databind" % versions.value.getProperty("jackson"),
  "javax.servlet" % "servlet-api" % "2.4" % "test",
  "com.novocode" % "junit-interface" % "0.11" % "test",
  "org.mockito" % "mockito-core" % versions.value.getProperty("mockito") % "test",
  "org.powermock" % "powermock-module-junit4" % versions.value.getProperty("powermock") % "test",
  "org.powermock" % "powermock-api-mockito" % versions.value.getProperty("powermock") % "test",
  "org.powermock" % "powermock-module-junit4-rule" % versions.value.getProperty("powermock") % "test",
  "org.powermock" % "powermock-classloading-xstream" % versions.value.getProperty("powermock") % "test",
  "org.hamcrest" % "hamcrest-all" % versions.value.getProperty("hamcrest") % "test"
)

unmanagedJars in Compile += file(sys.env("ATG_HOME") + "/DAS/lib/classes.jar")

unmanagedJars in Compile += file(sys.env("ATG_HOME") + "/DCS/lib/classes.jar")

unmanagedJars in Compile += file(sys.env("ATG_HOME") + "/Publishing/base/lib/classes.jar")

resolvers ++= Seq(
  "oss-releases" at "http://oss.jfrog.org/artifactory/libs-release/",
  "oss-snapshots" at "http://oss.jfrog.org/artifactory/libs-snapshot/",
  "Typesafe Snapshot Repository" at "http://repo.typesafe.com/typesafe/snapshots/",
  "Typesafe Release Repository" at "http://repo.typesafe.com/typesafe/releases/"
)

organization := "org.opencommercesearch"

publishMavenStyle := true

crossPaths := false

publishTo <<= version { version: String =>
  val baseUrl = "http://oss.jfrog.org/artifactory/"
  val (name, url) = if (version.contains("-SNAPSHOT"))
    ("oss-snapshots-pub", baseUrl + "oss-snapshot-local/")
  else
    ("oss-pub", baseUrl + "oss-release-local/")
  Some(Resolver.url(name, new URL(url))(Resolver.mavenStylePatterns))
}

// Resources


unmanagedResourceDirectories in Compile += baseDirectory.value / "src/main/config"

unmanagedResourceDirectories in Test += baseDirectory.value / "src/test/config"

unmanagedResourceDirectories in Test += baseDirectory.value / "testdata"

// Compiler

javacOptions in compile ++= Seq("-source", "1.6", "-target", "1.6", "-Xlint:none")

// Tests

fork in Test := true

javaOptions in Test += "-XX:-UseSplitVerifier"

parallelExecution in Test := true

// Jacoco

jacoco.settings

parallelExecution in jacoco.Config := false

jacoco.reportFormats in jacoco.Config := Seq(HTMLReport("utf-8"))

jacoco.excludes in jacoco.Config := Seq("views*", "*Routes*", "*controllers*routes*", "*controllers*Reverse*", "*controllers*javascript*", "*controller*ref*", "*Controller")

fork in jacoco.Config := true

javaOptions in jacoco.Config += "-XX:-UseSplitVerifier"


import de.johoop.jacoco4sbt.HTMLReport
import play.PlayImport.PlayKeys._

lazy val root = (project in file(".")).enablePlugins(PlayScala)

name := "opencommercesearch-api"

version := "0.7.10-SNAPSHOT"

scalaVersion := "2.11.1"

scalacOptions ++= Seq("-feature")

resolvers ++= Seq(
  Resolver.file("Local repo", file(System.getProperty("user.home") + "/.ivy2/local"))(Resolver.ivyStylePatterns),
  "sbt-plugin-releases" at "http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/",
  "sbt-plugin-snapshots" at "http://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots/",
  "sonatype-snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases"
)


libraryDependencies ++= Seq(
  cache, filters,
  "org.opencommercesearch" %% "play-solrj" % "0.5-SNAPSHOT",
  "org.opencommercesearch" %% "opencommercesearch-common" % "0.7.10-SNAPSHOT"  changing(),
  "com.typesafe.play.plugins" %% "play-statsd" % "2.3.0",
  "org.mockito" % "mockito-all" % "1.9.5" % "test",
  "org.hamcrest" % "hamcrest-all" % "1.3" % "test",
  "com.github.fakemongo" % "fongo" % "1.6.0" % "test",
  "com.wordnik" %% "swagger-play2" % "1.3.12",
  "org.reactivemongo" %% "reactivemongo" % "0.10.5.0.akka23",
  "org.apache.solr" % "solr-core" % "4.8.1" excludeAll(
    ExclusionRule(organization = "log4j"),
    ExclusionRule(organization = "org.slf4j", name = "slf4j-log4j12")
  )
)

organization := "org.opencommercesearch"

publishMavenStyle := true

publishTo <<= version { version: String =>
   val scalasbt = "http://repo.scala-sbt.org/scalasbt/"
   val (name, url) = if (version.contains("-SNAPSHOT"))
     ("sbt-plugin-snapshots-pub", scalasbt+"sbt-plugin-snapshots/")
   else
     ("sbt-plugin-releases-pub", scalasbt+"sbt-plugin-releases/")
   Some(Resolver.url(name, new URL(url))(Resolver.mavenStylePatterns))
}

packagedArtifacts += ((artifact in playPackageAssets).value -> playPackageAssets.value)

// Jacoco

jacoco.settings

parallelExecution in jacoco.Config := false

jacoco.reportFormats in jacoco.Config := Seq(HTMLReport("utf-8"))

jacoco.excludes in jacoco.Config := Seq("views*", "*Routes*", "*controllers*routes*", "*controllers*Reverse*", "*controllers*javascript*", "*controller*ref*", "*Controller")


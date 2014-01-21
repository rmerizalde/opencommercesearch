import sbt._
import Keys._
import play.Project._
import de.johoop.jacoco4sbt._
import JacocoPlugin._

object ApplicationBuild extends Build {

  val appName         = "opencommercesearch-api"
  val appVersion      = "0.3-SNAPSHOT"

  lazy val s = playScalaSettings ++ Seq(jacoco.settings:_*)

  val appDependencies: Seq[sbt.ModuleID] = Seq(
    cache,
    "org.opencommercesearch" %% "play-solrj" % "0.3-SNAPSHOT",
    "com.typesafe.play.plugins" %% "play-statsd" % "2.2.0",
    "org.mockito" % "mockito-all" % "1.9.5" % "test",
    "org.hamcrest" % "hamcrest-all" % "1.3" % "test",
    "com.wordnik" %% "swagger-play2" % "1.3.2",
    "de.undercouch" % "bson4jackson" % "2.2.3" force(),
    "com.fasterxml.jackson.core" % "jackson-databind" % "2.2.3" force(),
    "com.fasterxml.jackson.core" % "jackson-annotations" % "2.2.3" force(),
    "com.fasterxml.jackson.core" % "jackson-core" % "2.2.3" force(),
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.2.3",
    "org.mongodb" % "mongo-java-driver" % "2.11.3",
    "org.jongo" % "jongo" % "1.0"//,
    //"uk.co.panaxiom" %% "play-jongo" % "0.6.0-jongo0.4"
  )

  val main = play.Project(appName, appVersion, appDependencies, settings = s).settings(
  // @todo: publish play-solrj as maven style??
    resolvers += "sbt-plugin-releases" at "http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/",
    resolvers += "sbt-plugin-snapshots" at "http://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots/",
    resolvers += "sonatype-snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    resolvers += "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases",
    organization  := "org.opencommercesearch",
    publishMavenStyle := true,
    publishTo <<= (version) { version: String =>
       val scalasbt = "http://repo.scala-sbt.org/scalasbt/"
       val (name, url) = if (version.contains("-SNAPSHOT"))
         ("sbt-plugin-snapshots-pub", scalasbt+"sbt-plugin-snapshots/")
       else
         ("sbt-plugin-releases-pub", scalasbt+"sbt-plugin-releases/")
       Some(Resolver.url(name, new URL(url))(Resolver.mavenStylePatterns))
    },
    parallelExecution in jacoco.Config := false,
    jacoco.reportFormats in jacoco.Config := Seq(HTMLReport("utf-8")),
    jacoco.excludes in jacoco.Config := Seq("views*", "*Routes*", "*controllers*routes*", "*controllers*Reverse*", "*controllers*javascript*", "*controller*ref*", "*Controller")
  )

}

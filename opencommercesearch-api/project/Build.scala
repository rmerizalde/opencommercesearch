import sbt._
import Keys._
import play.Project._
import de.johoop.jacoco4sbt._
import JacocoPlugin._

object ApplicationBuild extends Build {

  val appName         = "opencommercesearch-api"
  val appVersion      = "0.1-SNAPSHOT"

  lazy val s = Defaults.defaultSettings ++ Seq(jacoco.settings:_*)

  val appDependencies = Seq(
    "org.opencommercesearch" %% "play-solrj" % "0.2-SNAPSHOT",
    "com.typesafe.play.plugins" %% "play-statsd" % "2.1.1",
    "org.mockito" % "mockito-all" % "1.9.5" % "test",
    "org.hamcrest" % "hamcrest-all" % "1.3" % "test"
  )


  val main = play.Project(appName, appVersion, appDependencies, settings = s).settings(
  // @todo: publish play-solrj as maven style??
    resolvers += "sbt-plugin-releases" at "http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/",
    resolvers += "sbt-plugin-snapshots" at "http://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots/",
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
    jacoco.reportFormats in jacoco.Config := Seq(XMLReport("utf-8"), HTMLReport("utf-8")),
    jacoco.excludes in jacoco.Config := Seq("default.*", "org.opencommercesearch.api.controllers.Reverse*", "org.opencommercesearch.api.controllers.javascript.*", "org.opencommercesearch.api.controllers.ref.*", "org.opencommercesearch.api.Routes*")
  )

}

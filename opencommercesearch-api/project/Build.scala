import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "opencommercesearch-api"
  val appVersion      = "0.1-SNAPSHOT"

  val appDependencies = Seq(
    "org.opencommercesearch" %% "play-solrj" % "0.2-SNAPSHOT",
    "com.typesafe.play.plugins" %% "play-statsd" % "2.1.1",
    "org.mockito" % "mockito-all" % "1.9.5" % "test"
  )


  val main = play.Project(appName, appVersion, appDependencies).settings(
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
    }
  )

}

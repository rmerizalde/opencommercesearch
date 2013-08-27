import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "opencommercesearch-api"
  val appVersion      = "0.1-SNAPSHOT"

  val appDependencies = Seq(
    "org.opencommercesearch" % "play-solrj_2.10" % "0.1",
    "com.typesafe.play.plugins" %% "play-statsd" % "2.1.1",
    "org.mockito" % "mockito-all" % "1.9.5" % "test"
  )


  val main = play.Project(appName, appVersion, appDependencies).settings(
    resolvers += Resolver.url("sbt-plugin-releases", new URL("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns),
    resolvers += Resolver.url("sbt-plugin-snapshots", new URL("http://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots/"))(Resolver.ivyStylePatterns),
    organization  := "org.opencommercesearch",
    publishMavenStyle := false,
    publishTo <<= (version) { version: String =>
       val scalasbt = "http://repo.scala-sbt.org/scalasbt/"
       val (name, url) = if (version.contains("-SNAPSHOT"))
         ("sbt-plugin-snapshots", scalasbt+"sbt-plugin-snapshots")
       else
         ("sbt-plugin-releases", scalasbt+"sbt-plugin-releases")
       Some(Resolver.url(name, new URL(url))(Resolver.ivyStylePatterns))
    }
  )

}

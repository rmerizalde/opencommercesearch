import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "opencommercesearch-api"
  val appVersion      = "0.1-SNAPSHOT"

  val appDependencies = Seq(
    "play-solrj" % "play-solrj_2.10" % "0.1-SNAPSHOT",
    "com.typesafe.play.plugins" %% "play-statsd" % "2.1.1",
    "org.mockito" % "mockito-all" % "1.9.5" % "test"
  )


  val main = play.Project(appName, appVersion, appDependencies).settings(
  )

}

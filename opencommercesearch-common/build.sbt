name := "opencommercesearch-common"

version := "0.3-SNAPSHOT"

scalaVersion := "2.10.2"

libraryDependencies += "org.opencommercesearch" %% "play-solrj" % "0.3-SNAPSHOT" % "provided"

resolvers ++= Seq(
  "SBT Plugin Snapshot Repository" at "http://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots/",
  "SBT Plugin Release Repository" at "http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/",
  "Typesafe Snapshot Repository" at "http://repo.typesafe.com/typesafe/snapshots/",
  "Typesafe Release Repository" at "http://repo.typesafe.com/typesafe/releases/"
)

organization := "org.opencommercesearch"

publishMavenStyle := true

publishTo <<= (version) { version: String =>
  val scalasbt = "http://repo.scala-sbt.org/scalasbt/"
  val (name, url) = if (version.contains("-SNAPSHOT"))
    ("sbt-plugin-snapshots", scalasbt+"sbt-plugin-snapshots")
  else
    ("sbt-plugin-releases", scalasbt+"sbt-plugin-releases")
  Some(Resolver.url(name, new URL(url))(Resolver.mavenStylePatterns))
}


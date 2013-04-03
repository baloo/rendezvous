import sbt._
import Keys._

object RendezvousBuild extends Build {
  val buildVersion = "0.9-SNAPSHOT"


  val buildSettings = Defaults.defaultSettings ++ Seq(
    scalaVersion := "2.10.0",
    organization := "io.nestin.rendezvous",
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
    scalacOptions in (Compile, doc) ++= Seq("-unchecked", "-deprecation", "-diagrams", "-implicits"),
    resolvers := Seq(
      "Typesafe snapshots" at "http://repo.typesafe.com/typesafe/snapshots/"
      ),
    libraryDependencies := Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.2-M2"
      )
    )

  

  lazy val root = Project(id = "rendezvous",
    base = file("."),
    settings = buildSettings)


}

lazy val scala212 = "2.12.8"
lazy val scala213 = "2.13.0"
lazy val supportedScalaVersions = List(scala213, scala212)

ThisBuild / organization := "com.github.irumiha"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := scala213

lazy val core = (project in file("core"))
  .settings(
    crossScalaVersions := supportedScalaVersions,
    name := "pgmirror-core",
    libraryDependencies ++= Seq(
      "org.scalatra.scalate" %% "scalate-core" % "1.9.4",
      "com.github.pathikrit" %% "better-files" % "3.8.0",
      "org.postgresql" % "postgresql" % "42.2.6",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test,
    )
  )

lazy val doobie = (project in file("doobie"))
  .settings(
    crossScalaVersions := supportedScalaVersions,
    name := "pgmirror-doobie"
  )
  .dependsOn(core)

lazy val pgmirror = (project in file("."))
  .settings(
    crossScalaVersions := Nil,
    publish / skip := true
  )
  .aggregate(core, doobie)

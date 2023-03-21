lazy val scala212 = "2.12.17"
lazy val scala213 = "2.13.10"
lazy val supportedScalaVersions = List(scala213, scala212)

ThisBuild / organization := "com.github.pgmirror"
ThisBuild / version      := "0.1.1-SNAPSHOT"
ThisBuild / scalaVersion := scala212

ThisBuild / githubTokenSource := Some(
  TokenSource.Environment("PGMIRROR_GITHUB_TOKEN"),
)
ThisBuild / githubOwner      := "pgmirror"
ThisBuild / githubRepository := "pgmirror"

lazy val core = (project in file("core"))
  .settings(
    crossScalaVersions := supportedScalaVersions,
    name               := "pgmirror-core",
    libraryDependencies ++= Seq(
      "org.postgresql" % "postgresql" % "42.5.4",
      "com.h2database" % "h2"         % "2.1.214",
      "org.scalatest" %% "scalatest"  % "3.2.15" % Test,
    ),
  )

lazy val scala = (project in file("scala-generator"))
  .settings(
    crossScalaVersions := supportedScalaVersions,
    name               := "pgmirror-scala-generator",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.15" % Test,
    ),
  )
  .dependsOn(core)


lazy val pgmirror = (project in file("."))
  .settings(
    crossScalaVersions := Nil,
    publish / skip     := true,
    scalacOptions ++= Seq(
      "-deprecation"
    )
  )
  .aggregate(core, scala)

lazy val scala212 = "2.12.14"
lazy val scala213 = "2.13.6"
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
      "org.postgresql" % "postgresql" % "42.2.23",
      "com.h2database" % "h2"         % "1.4.200",
      "org.scalatest" %% "scalatest"  % "3.2.9" % Test,
    ),
  )

lazy val scala = (project in file("scala-generator"))
  .settings(
    crossScalaVersions := supportedScalaVersions,
    name               := "pgmirror-scala-generator",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.9" % Test,
    ),
  )
  .dependsOn(core)


lazy val pgmirror = (project in file("."))
  .settings(
    crossScalaVersions := Nil,
    publish / skip     := true,
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "doobie-core"           % "1.0.0-M5",
      "org.tpolecat" %% "doobie-postgres"       % "1.0.0-M5",
      "org.tpolecat" %% "doobie-postgres-circe" % "1.0.0-M5",
    ),
    scalacOptions ++= Seq(
      "-deprecation"
    )
  )
  .aggregate(core, scala)

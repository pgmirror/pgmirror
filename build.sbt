lazy val scala212 = "2.12.11"
lazy val scala213 = "2.13.3"
lazy val supportedScalaVersions = List(scala213, scala212)

ThisBuild / organization := "com.github.pgmirror"
ThisBuild / version      := "0.1.1-SNAPSHOT"
ThisBuild / scalaVersion := scala212

ThisBuild / githubTokenSource := Some(
  TokenSource.Environment("PGMIRROR_GITHUB_TOKEN")
)
ThisBuild / githubOwner      := "pgmirror"
ThisBuild / githubRepository := "pgmirror"

lazy val core = (project in file("core"))
  .settings(
    crossScalaVersions := supportedScalaVersions,
    name               := "pgmirror-core",
    libraryDependencies ++= Seq(
      "org.postgresql"        % "postgresql"   % "42.2.14",
      "org.scalatest"        %% "scalatest"    % "3.2.0" % Test
    )
  )

lazy val doobie = (project in file("doobie"))
  .settings(
    crossScalaVersions := supportedScalaVersions,
    name               := "pgmirror-doobie"
  )
  .dependsOn(core)

lazy val pgmirror = (project in file("."))
  .settings(
    crossScalaVersions := Nil,
    publish / skip     := true,
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "doobie-core"           % "0.8.8",
      "org.tpolecat" %% "doobie-postgres"       % "0.8.8",
      "org.tpolecat" %% "doobie-postgres-circe" % "0.8.8",
      "io.circe"     %% "circe-core"            % "0.12.3",
      "io.circe"     %% "circe-generic"         % "0.12.3",
      "io.circe"     %% "circe-generic-extras"  % "0.12.2"
    )
  )
  .aggregate(core, doobie)

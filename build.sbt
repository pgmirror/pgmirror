lazy val commonSettings = Seq(
  version := "0.1",
  scalaVersion := "2.12.8",
  organization := "com.github.irumiha",
)

lazy val core = (project in file("core"))
  .settings(commonSettings)
  .settings(
    name := "pgmirror-core",
    libraryDependencies ++= Seq(
      "org.scalatra.scalate" %% "scalate-core" % "1.9.4-RC1",
      "com.github.pathikrit" %% "better-files" % "3.8.0",
      "io.tmos" %% "arm4s" % "1.1.0",
      "org.postgresql" % "postgresql" % "42.2.6",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test,
    )
  )

lazy val doobie = (project in file("doobie"))
  .settings(commonSettings)
  .settings(name := "pgmirror-doobie")
  .dependsOn(core)


lazy val pgmirror = (project in file("."))
  .settings(commonSettings)
  .aggregate (core, doobie)

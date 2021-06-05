package com.github.pgmirror.generator.scala

import com.github.pgmirror.core.Settings

object Main extends App {
  val genSettings = Settings(
    url = "jdbc:postgresql:taop",
    user = "taop",
    password = "taop",
    rootPath = "src/main/scala",
    rootPackage = "com.acme.taop.model",
    defaultSchema = "",
    schemas = Set(
      "chinook",
      "imdb",
      "lastfm",
      "magic",
      "raw",
      "sample",
      "sandbox",
    )
  )

  new DoobieRepositoryGenerator(genSettings).generate("org.postgresql.Driver")
  new ScalaCaseClassGenerator(genSettings).generate("org.postgresql.Driver")

}

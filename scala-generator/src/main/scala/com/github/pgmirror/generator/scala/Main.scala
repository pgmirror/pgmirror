package com.github.pgmirror.generator.scala

import com.github.pgmirror.core.Settings

object Main extends App {
  Class.forName("org.postgresql.Driver")
  new DoobieGenerator(
    Settings(
      url = args(0),
      user = args(1),
      password = args(2),
      rootPath = args(3),
      rootPackage = args(4),
      schemas = Set("public", "auth"),
      defaultSchema = "",
    ),
  ).generate()
}

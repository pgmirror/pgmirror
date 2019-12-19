package com.github.irumiha.pgmirror.doobie

import java.sql.DriverManager

import com.github.irumiha.pgmirror.doobie.DoobieGenerator
import com.github.irumiha.pgmirror.Settings

object Main extends App {
  Class.forName("org.postgresql.Driver")
  val conn = DriverManager.getConnection("jdbc:postgresql://localhost/artisai", "postgres", "postgres")

  new DoobieGenerator()
    .generate(
      Settings(
        url = args(0),
        user = args(1),
        password = args(2),
        rootPath = args(3),
        rootPackage = args(4)
      )
    )
}

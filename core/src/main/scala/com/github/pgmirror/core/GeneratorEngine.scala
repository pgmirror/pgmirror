package com.github.pgmirror.core

import com.github.pgmirror.core.model.generator.Database

import java.io.File
import java.sql.DriverManager

class GeneratorEngine(settings: Settings, generatorProducers: (Settings) => Generator*) {
  val generators: Seq[Generator] = generatorProducers.map(_(settings))

  def generate(): Seq[File] = {
    assert(generators.nonEmpty)

    Class.forName(settings.driverClass)
    val conn = DriverManager.getConnection(
      settings.url,
      settings.user,
      settings.password
    )

    Database.build(settings, conn).map { database =>
      generators.flatMap{ g =>
        g.generateForAllTables(database.tables, database.views, database.foreignKeys)
      }
    }
  }.get
}

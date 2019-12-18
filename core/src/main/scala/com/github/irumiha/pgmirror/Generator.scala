package com.github.irumiha.pgmirror

import java.nio.charset.StandardCharsets

import better.files.File
import com.github.irumiha.pgmirror.model.generator.{ForeignKey, TableLike}

case class GeneratedFile(relativePath: String, filename: String, content: String)

abstract class Generator {

  def generate(settings: Settings): Unit = {
    (for (
      database <- new DatabaseSchemaGatherer(settings).gatherDatabase
    ) yield {
      generateForAllTables(settings, database.tables, database.foreignKeys)
      generateForAllTables(settings, database.views, database.foreignKeys)
    }) match {
      case Left(errors) =>
        errors.foreach(println)
      case _ =>
        println("Done!")
    }

  }

  def generateForAllTables(settings: Settings, tables: List[TableLike], foreignKeys: List[ForeignKey]): Unit = {
    import settings._

    val rootOutputDir = rootPackage match {
      case "" => File(rootPath)
      case _  => File(s"$rootPath/${rootPackage.replace(".", "/")}")
    }

    rootOutputDir.createDirectories()

    val allTableFiles = tables.flatMap(generateForTable(settings, _, foreignKeys))
    val utilFile = generateUtil(settings).toList

    (allTableFiles ++ utilFile).foreach { ts =>
      val fileOutputDir = rootOutputDir / ts.relativePath
      fileOutputDir.createDirectories()
      val file = fileOutputDir / ts.filename
      file.write(ts.content)(charset = StandardCharsets.UTF_8)
    }
  }

  def generateForTable(settings: Settings, table: TableLike, foreignKeys: List[ForeignKey]): List[GeneratedFile]

  def generateUtil(settings: Settings): Option[GeneratedFile]

}

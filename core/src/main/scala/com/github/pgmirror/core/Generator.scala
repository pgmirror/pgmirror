package com.github.pgmirror.core

import java.nio.charset.StandardCharsets

import better.files.File
import com.github.pgmirror.core.model.generator.{ForeignKey, TableLike}

case class GeneratedFile(relativePath: String, filename: String, content: String)

abstract class Generator(settings: Settings) {

  def generate(): Unit = {
    (for (
      database <- new DatabaseSchemaGatherer(settings).gatherDatabase
    ) yield {
      generateForAllTables(database.tables, database.foreignKeys)
      generateForAllTables(database.views, database.foreignKeys)
    }) match {
      case Left(errors) =>
        errors.foreach(println)
      case _ =>
        println("Done!")
    }

  }

  def generateForAllTables(tables: List[TableLike], foreignKeys: List[ForeignKey]): Unit = {
    import settings._

    val rootOutputDir = rootPackage match {
      case "" => File(rootPath)
      case _  => File(s"$rootPath/${rootPackage.replace(".", "/")}")
    }

    rootOutputDir.createDirectories()

    val allTableFiles = tables.flatMap(generateForTable(_, foreignKeys))
    val utilFile = generateUtil.toList

    (allTableFiles ++ utilFile).foreach { ts =>
      val fileOutputDir = rootOutputDir / ts.relativePath
      fileOutputDir.createDirectories()
      val file = fileOutputDir / ts.filename
      file.write(ts.content)(charset = StandardCharsets.UTF_8)
    }
  }

  def generateForTable(table: TableLike, foreignKeys: List[ForeignKey]): List[GeneratedFile]

  def generateUtil: Option[GeneratedFile]

}

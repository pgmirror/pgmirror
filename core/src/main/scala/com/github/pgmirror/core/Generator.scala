package com.github.pgmirror.core

import java.nio.charset.StandardCharsets

import better.files.File
import com.github.pgmirror.core.model.generator.{ForeignKey, TableLike}

case class GeneratedFile(
    relativePath: String,
    filename: String,
    content: String
)

abstract class Generator(settings: Settings) {

  /**
    * The base method that runs the whole process of gathering database schema
    * and running the generators.
    */
  final def generate(): Seq[File] = {
    (for {
      database <- new DatabaseSchemaGatherer(settings).gatherDatabase
      file <- Right(
               generateForAllTables(
                 database.tables ++ database.views,
                 database.foreignKeys
               )
             )
    } yield file) match {
      case Left(errors) =>
        errors.foreach(println)
        Seq()
      case Right(files) =>
        println("Done!")
        files
    }

  }

  /**
    * For all tables call the generators and output the returned contents into spedified file paths.
    *
    * @param tables List of all table-like objects (tables and views).
    * @param foreignKeys List of all foreign keys in the schema.
    */
  private final def generateForAllTables(
      tables: List[TableLike],
      foreignKeys: List[ForeignKey]
  ): Seq[File] = {
    import settings._

    val rootOutputDir = rootPackage match {
      case "" => File(rootPath)
      case _  => File(s"$rootPath/${rootPackage.replace(".", "/")}")
    }

    rootOutputDir.createDirectories()

    val allTableFiles = tables.flatMap(generateForTable(_, foreignKeys))
    val utilFile      = generateUtil.toList

    (allTableFiles ++ utilFile).map { ts =>
      val fileOutputDir = rootOutputDir / ts.relativePath
      fileOutputDir.createDirectories()
      val file = fileOutputDir / ts.filename
      file.write(ts.content)(charset = StandardCharsets.UTF_8)

      file
    }
  }

  /**
    * Generates zero or more files for a given table.
    *
    * @param table The table the code is generated for.
    * @param foreignKeys List of ALL foreign keys in the schema.
    * @return List of GeneratedFile containing the path and contents for each file.
    */
  def generateForTable(
      table: TableLike,
      foreignKeys: List[ForeignKey]
  ): List[GeneratedFile]

  /**
    * Generates a single utility file that is not dependent on actual database schema.
    * Use it to generate model or repository superclasses, etc.
    * @return
    */
  def generateUtil: Option[GeneratedFile]

}

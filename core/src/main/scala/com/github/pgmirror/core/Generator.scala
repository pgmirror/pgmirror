package com.github.pgmirror.core

import java.io.File
import java.nio.file.Files

import com.github.pgmirror.core.model.generator.{ForeignKey, Table, View}

case class GeneratedFile(
  relativePath: String,
  filename: String,
  content: String,
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
          database.tables,
          database.views,
          database.foreignKeys,
        ),
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
  final private def generateForAllTables(
    tables: List[Table],
    views: List[View],
    foreignKeys: List[ForeignKey],
  ): Seq[File] = {
    import settings._

    val rootOutputDir = rootPackage match {
      case "" => new File(rootPath)
      case _  => new File(s"$rootPath/${rootPackage.replace(".", "/")}")
    }

    rootOutputDir.mkdirs()

    val allTableFiles = tables.flatMap(generateForTable(_, foreignKeys))
    val allViewFiles = views.flatMap(generateForView)
    val utilFile = generateUtil.toList

    (allTableFiles ++ allViewFiles ++ utilFile).map { ts =>
      val fileOutputDir = new File(rootOutputDir, ts.relativePath)
      fileOutputDir.mkdirs()
      val file = new File(fileOutputDir, ts.filename)
      Files.writeString(file.toPath, ts.content)

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
    table: Table,
    foreignKeys: List[ForeignKey],
  ): List[GeneratedFile]

  /**
    * Generates zero or more files for a given view.
    *
    * @param view The view the code is generated for.
    * @return List of GeneratedFile containing the path and contents for each file.
    */
  def generateForView(
    view: View,
  ): List[GeneratedFile]

  /**
    * Generates a single utility file that is not dependent on actual database schema.
    * Use it to generate model or repository superclasses, etc.
    * @return
    */
  def generateUtil: Option[GeneratedFile]

}

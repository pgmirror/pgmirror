package com.github.pgmirror.core

import com.github.pgmirror.core.model.generator.{ForeignKey, Table, View}

import java.io.File
import java.nio.file.Files

case class GeneratedFile(
  relativePath: String,
  filename: String,
  content: String,
)

abstract class Generator(settings: Settings) {

  /**
    * Run the whole process
    */
  final def generate(): Seq[File] = {
    ???
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

    val rootOutputDir: File = createRootOutputDir(rootPath, rootPackage)

    val allTableFiles = tables.flatMap(generateForTable(_, foreignKeys))
    val allViewFiles = views.flatMap(generateForView)
    val utilFile = generateUtil

    (allTableFiles ++ allViewFiles ++ utilFile).map { ts =>
      val fileOutputDir = new File(rootOutputDir, ts.relativePath)
      fileOutputDir.mkdirs()
      val file = new File(fileOutputDir, ts.filename)
      Files.writeString(file.toPath, ts.content)

      file
    }
  }

  private def createRootOutputDir(rootPath: String, rootPackage: String) = {
    val rootOutputDir = rootPackage match {
      case "" => new File(rootPath)
      case _ => new File(s"$rootPath/${rootPackage.replace(".", "/")}")
    }

    rootOutputDir.mkdirs()
    rootOutputDir
  }

  /**
    * Generates zero or more files for a given table.
    *
    * @param table The table the code is generated for.
    * @param foreignKeys List of ALL foreign keys in the schema.
    * @return List of GeneratedFile containing the path and contents for each file.
    */
  def generateForTable(table: Table, foreignKeys: List[ForeignKey]): List[GeneratedFile]

  /**
    * Generates zero or more files for a given view.
    *
    * @param view The view the code is generated for.
    * @return List of GeneratedFile containing the path and contents for each file.
    */
  def generateForView(view: View): List[GeneratedFile]

  /**
    * Generates utility file(s) that are not dependent on actual database schema.
    * Use it to generate model or repository superclasses, etc.
    * @return
    */
  def generateUtil: List[GeneratedFile]

}

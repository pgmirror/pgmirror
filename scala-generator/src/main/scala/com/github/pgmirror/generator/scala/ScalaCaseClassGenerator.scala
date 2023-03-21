package com.github.pgmirror.generator.scala

import com.github.pgmirror.core.model.generator._
import com.github.pgmirror.core.{GeneratedFile, Generator, Names, Settings}
import com.github.pgmirror.generator.scala.ScalaCommon._

import java.io.File

class ScalaCaseClassGenerator(settings: Settings) extends Generator(settings) {

  /** Generates zero or more files for a given table.
    *
    * @param table       The table the code is generated for.
    * @param foreignKeys List of ALL foreign keys in the schema.
    * @return List of GeneratedFile containing the path and contents for each file.
    */
  override def generateForTable(table: Table): List[GeneratedFile] =
    generateCaseClass(table)

  /** Generates zero or more files for a given view.
    *
    * @param view The view the code is generated for.
    * @return List of GeneratedFile containing the path and contents for each file.
    */
  override def generateForView(view: View): List[GeneratedFile] =
    generateCaseClass(view)

  private def generateCaseClass(table: NamedWithSchema with Columns) = {
    List(
      GeneratedFile(
        Seq(table.schemaName).filterNot(_.isEmpty).mkString(File.separator),
        Names.toClassCamelCase(table.name) + ".scala",
        generateDataClass(settings, table),
      ),
    )
  }

  /** Generates utility file(s) that are not dependent on actual database schema.
    * Use it to generate model or repository superclasses, etc.
    *
    * @return
    */
  override def generateUtil: List[GeneratedFile] = List()

  private def generateDataClass(settings: Settings, table: NamedWithSchema with Columns): String = {

    def columnWithDefault(column: Column) =
      scalaPropWithComment(settings.rootPackage, column) + " = " + columnDefault(
        scalaPropType(settings.rootPackage, column),
      )

    val columnList =
      table.columns
        .map(c => if (c.hasDefault) columnWithDefault(c) else scalaPropWithComment(settings.rootPackage, c))
        .map("  " + _)
        .mkString(",\n  ")

    val className = Names.toClassCamelCase(table.name)
    val classPackage = tablePackage(settings.rootPackage, table.schemaName)

    val imports =
      if (settings.generateCirce) {
        """import io.circe.{Decoder, Encoder}
          |import io.circe.generic.extras.semiauto._
          |import io.circe.generic.extras.Configuration""".stripMargin
      } else {
        ""
      }

    val companion =
      if (settings.generateCirce) {
        s"""object $className {
           |
           |  implicit val customConfig: Configuration = Configuration.default.withSnakeCaseMemberNames
           |
           |  implicit val circeDecoder: Decoder[$className] = deriveConfiguredDecoder
           |
           |  implicit val circeEncoder: Encoder[$className] = deriveConfiguredEncoder
           |
           |}
           |""".stripMargin
      } else {
        ""
      }

    s"""package $classPackage
       |
       |$imports
       |
       |case class $className (
       |  $columnList
       |)
       |
       |$companion
       |""".stripMargin
  }

}

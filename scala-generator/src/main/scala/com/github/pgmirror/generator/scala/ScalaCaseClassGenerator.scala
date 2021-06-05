package com.github.pgmirror.generator.scala

import com.github.pgmirror.core.model.generator.{Column, Columns, NamedWithSchema}
import com.github.pgmirror.core.{Names, Settings}
import com.github.pgmirror.generator.scala.ScalaCommon._

class ScalaCaseClassGenerator {

  private def generateDataClass(settings: Settings, table: NamedWithSchema with Columns): String = {

    def columnWithDefault(column: Column) =
      scalaPropWithComment(settings.rootPackage, column) + " = " + columnDefault(scalaPropType(settings.rootPackage, column))

    val columnList =
      table.columns
        .map(c => if (c.hasDefault) columnWithDefault(c) else scalaPropWithComment(settings.rootPackage, c))
        .mkString(",\n  ")

    s"""package ${tablePackage(settings.rootPackage, table.schemaName)}
       |
       |import io.circe.{Decoder, Encoder}
       |import io.circe.generic.extras.semiauto._
       |import io.circe.generic.extras.Configuration
       |
       |case class ${Names.toClassCamelCase(table.name)} (
       |  $columnList
       |)
       |
       |object ${Names.toClassCamelCase(table.name)} {
       |
       |  implicit val customConfig: Configuration = Configuration.default.withSnakeCaseMemberNames
       |
       |  implicit val decode${Names.toClassCamelCase(table.name)}: Decoder[${Names
      .toClassCamelCase(table.name)}] = deriveConfiguredDecoder
       |
       |  implicit val encode${Names.toClassCamelCase(table.name)}: Encoder[${Names
      .toClassCamelCase(table.name)}] = deriveConfiguredEncoder
       |
       |}
       |
       |""".stripMargin
  }


}

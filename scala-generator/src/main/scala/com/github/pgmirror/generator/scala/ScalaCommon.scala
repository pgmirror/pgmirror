package com.github.pgmirror.generator.scala

import com.github.pgmirror.core.Names
import com.github.pgmirror.core.model.generator.{Column, SqlTypes}

object ScalaCommon {

  def tablePackage(rootPackage: String, schemaName: String): String =
    List(rootPackage, schemaName).filterNot(_.isEmpty).mkString(".")

  def modelType(rootPackage: String, column: Column): String =
    SqlTypes.typeMapping(rootPackage, column.columnType).get.modelType

  def scalaPropType(rootPackage: String, column: Column): String = {
    if (column.isNullable) {
      s"""Option[${modelType(rootPackage, column)}]"""
    } else {
      s"""${modelType(rootPackage, column)}"""
    }
  }

  def scalaPropName(column: Column): String = {
    val rawPropName: String = Names.toPropertyCamelCase(column.name)

    if (
      rawPropName.matches("""^\d.*""")
        || rawPropName == "type"
        || rawPropName == "final"
        || rawPropName == "class"
    )
      "`" + rawPropName + "`"
    else
      rawPropName
  }

  def scalaProp(rootPackage: String, column: Column): String =
    s"""${scalaPropName(column)}: ${scalaPropType(rootPackage, column)}"""

  def scalaPropWithComment(rootPackage: String, column: Column): String = {
    val commentOut = column.comment
      .map { co =>
        co.trim.split("\n").map(l => s"// $l").mkString("\n|  ") + "\n|  "
      }
      .getOrElse("")

    s"""$commentOut${scalaPropName(column)}: ${scalaPropType(rootPackage, column)}"""
  }

  def tableColumn(column: Column): String = s""""${column.tableName}"."${column.name}""""

  def columnNameQuoted(column: Column): String = s""""${column.name}""""

  def columnDefault(p: String): String =
    p match {
      case "Int"                       => "Int.MinValue"
      case "Long"                      => "Long.MinValue"
      case "Float"                     => "Float.NaN"
      case "Double"                    => "Double.NaN"
      case "BigDecimal"                => "BigDecimal(0)"
      case "Boolean"                   => "false"
      case "Array[Byte]"               => "Array[Byte]()"
      case "String"                    => "\"\""
      case "java.util.UUID"            => "new java.util.UUID(0,0)"
      case "java.time.LocalDate"       => "java.time.LocalDate.MIN"
      case "java.time.LocalTime"       => "java.time.LocalTime.MIN"
      case "java.time.Instant"         => "java.time.Instant.MIN"
      case "java.time.LocalDateTime"   => "java.time.LocalDateTime.MIN"
      case t if t.startsWith("Array[") => s"$t()"
      case t                           => throw new UnsupportedOperationException(s"Unsupported primary key type: $t")
    }

}

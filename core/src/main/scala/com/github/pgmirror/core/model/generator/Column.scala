package com.github.pgmirror.core.model.generator

import com.github.pgmirror.core.Names

case class Column(
  tableSchema: String,
  tableName: String,
  name: String,
  columnType: String,
  typeName: String,
  modelType: String,
  isNullable: Boolean,
  isPrimaryKey: Boolean,
  ordinalPosition: Int,
  comment: Option[String],
  annotations: List[ColumnAnnotation] = Nil,
  hasDefault: Boolean,
) {

  def scalaPropType: String = {
    if (isNullable) {
      s"""Option[$modelType]"""
    } else {
      s"""$modelType"""
    }
  }

  def scalaPropName: String = {
    val rawPropName: String = Names.toPropertyCamelCase(name)

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

  def scalaProp: String =
    s"""$scalaPropName: $scalaPropType"""

  def scalaPropWithComment: String = {
    val commentOut = comment
      .map { co =>
        co.trim.split("\n").map(l => s"// $l").mkString("\n|  ") + "\n|  "
      }
      .getOrElse("")

    s"""$commentOut$scalaPropName: $scalaPropType"""
  }

  def tableColumn: String = s""""$tableName"."$name""""

  def columnNameQuoted: String = s""""$name""""

}

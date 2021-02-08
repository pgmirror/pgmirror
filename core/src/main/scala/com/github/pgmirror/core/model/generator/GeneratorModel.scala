package com.github.pgmirror.core.model.generator

import com.github.pgmirror.core.Names
import com.github.pgmirror.core.model.database

import scala.util.matching.Regex

sealed abstract class Annotation(val regex: Regex)

sealed abstract class ColumnAnnotation(override val regex: Regex) extends Annotation(regex)

object ColumnAnnotation {
  case object Detail     extends ColumnAnnotation("""@Detail\b""".r)
  case object FilterEq   extends ColumnAnnotation("""@FilterEQ\b""".r)
  case object FilterGt   extends ColumnAnnotation("""@FilterGT\b""".r)
  case object FilterLt   extends ColumnAnnotation("""@FilterLT\b""".r)
  case object FilterGtEq extends ColumnAnnotation("""@FilterGE\b""".r)
  case object FilterLtEq extends ColumnAnnotation("""@FilterLE\b""".r)
  case object Find       extends ColumnAnnotation("""@Find\b""".r)
  case object FindOne    extends ColumnAnnotation("""@FindOne\b""".r)
  case object NotNull    extends ColumnAnnotation("""@NotNull\b""".r)
  case object Versioning extends ColumnAnnotation("""@Versioning\b""".r)

  val values: List[ColumnAnnotation] = List[ColumnAnnotation](
    Detail,
    FilterEq,
    FilterGt,
    FilterLt,
    FilterGtEq,
    FilterLtEq,
    Find,
    FindOne,
    NotNull,
    Versioning,
  )

  val filterValues: Set[ColumnAnnotation] = Set(
    FilterEq,
    FilterGt,
    FilterLt,
    FilterGtEq,
    FilterLtEq,
  )

  def findAllForColumn(in: database.Column): List[ColumnAnnotation] =
    values
      .filter(_.regex.findAllIn(in.description.getOrElse("")).nonEmpty)
}

sealed abstract class TableAnnotation(override val regex: Regex) extends Annotation(regex)

object TableAnnotation {
  case object Limit        extends TableAnnotation("""@Limit\b""".r)
  case object Offset       extends TableAnnotation("""@Offset\b""".r)
  case object Lookup       extends TableAnnotation("""@Lookup\b""".r)
  case object Event        extends TableAnnotation("""@Event\b""".r)
  case object VersionCheck extends TableAnnotation("""@VersionCheck\b""".r)
  case object History      extends TableAnnotation("""@History\b""".r)

  val values: List[TableAnnotation] = List[TableAnnotation](
    Limit,
    Offset,
    Lookup,
    Event,
    VersionCheck,
    History,
  )

  def findAllForTable(in: database.Table): List[TableAnnotation] =
    values
      .filter(_.regex.findAllIn(in.description.getOrElse("")).nonEmpty)
}

case class TableLike(
  schemaName: String,
  name: String,
  className: String,
  columns: List[Column],
  comment: Option[String],
  foreignKeys: List[ForeignKey] = List(),
  isUpdatable: Boolean = false,
  isInsertable: Boolean = false,
  annotations: List[TableAnnotation] = Nil,
) {
  def nameWithSchema: String =
    List(s""""$schemaName"""", s""""$name"""").filterNot(_.isEmpty).mkString(".")
}

case class Table(value: TableLike) extends AnyVal
case class View(value: TableLike)  extends AnyVal
case class Udt(value: TableLike)   extends AnyVal

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

case class ForeignKey(
  table: Table,
  column: Column,
  foreignTable: Table,
  foreignColumn: Column,
)

case class Database(
  tables: List[Table],
  views: List[View],
  udts: List[Udt],
  foreignKeys: List[ForeignKey],
)

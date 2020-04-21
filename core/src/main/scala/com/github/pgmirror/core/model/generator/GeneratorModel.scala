package com.github.pgmirror.core.model.generator

import com.github.pgmirror.core.model.gatherer.{PgColumn, PgTable}

import scala.util.matching.Regex

sealed trait TableType

case object Table extends TableType

case object View extends TableType

case object Udt extends TableType

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

  def findAllFor(in: PgColumn): List[ColumnAnnotation] =
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

  def findAllFor(in: PgTable): List[TableAnnotation] =
    values
      .filter(_.regex.findAllIn(in.description.getOrElse("")).nonEmpty)
}

case class TableLike(
  tableType: TableType,
  schemaName: String,
  name: String,
  className: String,
  columns: List[Column],
  comment: Option[String],
  foreignKeys: List[ForeignKey] = List(),
  isView: Boolean = false,
  viewIsUpdatable: Boolean = false,
  isInsertable: Boolean = false,
  annotations: List[TableAnnotation] = Nil,
) {
  def tableWithSchema: String =
    List(schemaName, s""""$name"""").filterNot(_.isEmpty).mkString(".")
}

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

  def propType: String = {
    if (isNullable) {
      s"""Option[$modelType]"""
    } else {
      s"""$modelType"""
    }
  }

  def propName: String = {
    val nameParts: Array[String] = name.split("_")

    val rawPropName = nameParts.head + nameParts.tail.map(_.capitalize).mkString

    if (rawPropName.matches("""^\d.*""")
        || rawPropName == "type"
        || rawPropName == "final"
        || rawPropName == "class")
      "`" + rawPropName + "`"
    else
      rawPropName
  }

  def prop: String =
    s"""${propName}: ${propType}"""

  def propWithComment: String = {
    val commentOut = comment
      .map { co =>
        co.trim.split("\n").map(l => s"// $l").mkString("\n|  ") + "\n|  "
      }
      .getOrElse("")

    s"""$commentOut$propName: $propType"""
  }

  def tableColumn: String = s""""${tableName}"."${name}""""

  def columnNameQuoted: String = s""""$name""""

}

case class ForeignKey(
  table: TableLike,
  column: Column,
  foreignTable: TableLike,
  foreignColumn: Column,
)

case class Database(
  tables: List[TableLike],
  views: List[TableLike],
  udts: List[TableLike],
  foreignKeys: List[ForeignKey],
)

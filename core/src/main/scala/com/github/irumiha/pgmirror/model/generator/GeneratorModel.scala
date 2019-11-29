package com.github.irumiha.pgmirror.model.generator

import scala.util.matching.Regex

sealed trait TableType
case object Table extends TableType
case object View extends TableType
case object Udt extends TableType

sealed abstract class Annotation(val regex: Regex)

sealed abstract class ColumnAnnotation(override val regex: Regex) extends Annotation(regex)

object ColumnAnnotation {
  case object FilterEq      extends ColumnAnnotation("@FilterEQ".r)
  case object FilterGt      extends ColumnAnnotation("@FilterGT".r)
  case object FilterLt      extends ColumnAnnotation("@FilterLT".r)
  case object FilterGtEq    extends ColumnAnnotation("@FilterGE".r)
  case object FilterLtEq    extends ColumnAnnotation("@FilterLE".r)
  case object BelongsTo     extends ColumnAnnotation("@BelongsTo".r)
  case object Detail        extends ColumnAnnotation("@Detail".r)
  case object Versioning    extends ColumnAnnotation("@Versioning".r)

  val values: List[ColumnAnnotation] = List[ColumnAnnotation](
    FilterEq, FilterGt, FilterLt, FilterGtEq, FilterLtEq, BelongsTo, Detail, Versioning
  )

}

sealed abstract class TableAnnotation(override val regex: Regex) extends Annotation(regex)

object TableAnnotation {
  case object Limit        extends TableAnnotation("@Limit".r)
  case object Offset       extends TableAnnotation("@Offset".r)
  case object Lookup       extends TableAnnotation("@Lookup".r)
  case object Event        extends TableAnnotation("@Event".r)
  case object VersionCheck extends TableAnnotation("@VersionCheck".r)
  case object History      extends TableAnnotation("@History".r)

  val values: List[TableAnnotation] = List[TableAnnotation](
    Limit, Offset, Lookup, Event, VersionCheck, History
  )
}
case class TableLike(
  tableType: TableType,
  schemaName: String,
  tableName: String,
  tableClassName: String,
  columns: List[Column],
  comment: Option[String],
  foreignKeys: List[ForeignKey] = List(),
  isView: Boolean = false,
  viewIsUpdatable: Boolean = false,
  isInsertable: Boolean = false,
  annotations: List[TableAnnotation] = Nil
) {
  def tableWithSchema: String =
    List(schemaName, s""""$tableName"""").filterNot(_.isEmpty).mkString(".")
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
  annotations: List[ColumnAnnotation] = Nil
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

    nameParts.head + nameParts.tail.map(_.capitalize).mkString
  }

  def prop: String =
    s"""${propName}: ${propType}"""

  def propWithComment: String =
    s"""${comment.map(co => s"// $co\n|  ").getOrElse("")}${propName}: ${propType}"""

  def tableColumn: String = s""""${tableName}"."${name}""""

}

case class ForeignKey(
  table: TableLike,
  column: Column,
  foreignTable: TableLike,
  foreignColumn: Column
)

case class Database(
  tables: List[TableLike],
  views: List[TableLike],
  udts: List[TableLike],
  foreignKeys: List[ForeignKey]
)

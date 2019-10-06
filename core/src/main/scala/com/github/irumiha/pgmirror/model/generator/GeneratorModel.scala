package com.github.irumiha.pgmirror.model.generator

import scala.util.matching.Regex

sealed trait TableType
case object Table extends TableType
case object View extends TableType
case object Udt extends TableType

sealed abstract class Annotation(val regex: Regex)

sealed abstract class ColumnAnnotation(override val regex: Regex) extends Annotation(regex)

object ColumnAnnotation {
  case object FilterEq                          extends ColumnAnnotation("@FilterEQ".r)
  case object FilterGt                          extends ColumnAnnotation("@FilterGT".r)
  case object FilterLt                          extends ColumnAnnotation("@FilterLT".r)
  case object FilterGtEq                        extends ColumnAnnotation("@FilterGE".r)
  case object FilterLtEq                        extends ColumnAnnotation("@FilterLE".r)
  case object FilterBetween                     extends ColumnAnnotation("@FilterBetween".r)
  case object BelongsTo                         extends ColumnAnnotation("@BelongsTo".r)
  case object Detail                            extends ColumnAnnotation("@Detail".r)
  case object Versioning                        extends ColumnAnnotation("@Versioning".r)
  case class  Command(commandName: String = "") extends ColumnAnnotation("@Command\\((?<commandname>\\S+)\\)".r)

  val values: List[ColumnAnnotation] = List[ColumnAnnotation](
    FilterEq, FilterGt, FilterLt, FilterGtEq, FilterLtEq, FilterBetween, BelongsTo, Detail, Versioning, Command()
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
)

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
)

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

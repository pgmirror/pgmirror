package com.github.irumiha.pgmirror.model.generator

sealed trait TableType
case object Table extends TableType
case object View extends TableType
case object Udt extends TableType

sealed trait ColumnAnnotation
case object FilterEq                     extends ColumnAnnotation
case object FilterGt                     extends ColumnAnnotation
case object FilterLt                     extends ColumnAnnotation
case object FilterGtEq                   extends ColumnAnnotation
case object FilterLtEq                   extends ColumnAnnotation
case object FilterBetween                extends ColumnAnnotation
case object BelongsTo                    extends ColumnAnnotation
case object Detail                       extends ColumnAnnotation
case object Versioning                   extends ColumnAnnotation
case class  Command(commandName: String) extends ColumnAnnotation

sealed trait TableAnnotation
case object Limit                        extends TableAnnotation
case object Offset                       extends TableAnnotation
case object Lookup                       extends TableAnnotation
case object Event                        extends TableAnnotation
case object VersionCheck                 extends TableAnnotation
case object History                      extends TableAnnotation

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

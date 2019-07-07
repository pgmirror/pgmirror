package com.github.irumiha.pgmirror.model.generator

sealed trait TableType
case object Table extends TableType
case object View extends TableType
case object Udt extends TableType

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
  isInsertable: Boolean = false
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
  comment: Option[String]
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

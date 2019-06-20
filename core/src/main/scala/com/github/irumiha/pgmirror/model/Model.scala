package com.github.irumiha.pgmirror.model

case class Table(schemaName: String, tableName: String, columns: List[Column], comment: Option[String])

case class View(schemaName: String, tableName: String, columns: List[Column], comment: Option[String], isUpdatable: Boolean, isInsertable: Boolean)

case class Udt(schemaName: String, udtName: String, columns: List[Column], comment: Option[String])

case class Column(name: String, columnType: String, typeName: String, modelType: String, isNullable: Boolean, isPrimaryKey: Boolean, comment: Option[String])

case class Database(tables: List[Table], views: List[View], udts: List[Udt])

package com.github.pgmirror.core.model.generator

case class Column(
  tableSchema: String,
  tableName: String,
  name: String,
  columnType: String,
  isNullable: Boolean,
  isPrimaryKey: Boolean,
  ordinalPosition: Int,
  comment: Option[String],
  annotations: List[ColumnAnnotation] = Nil,
  hasDefault: Boolean,
)

package com.github.pgmirror.core.model.generator

case class Table(
  schemaName: String,
  name: String,
  columns: List[Column],
  comment: Option[String] = None,
  foreignKeys: List[ForeignKey] = List(),
  annotations: List[TableAnnotation] = List(),
) extends NamedWithSchema
    with Columns

package com.github.pgmirror.core.model.generator

case class View(
  schemaName: String,
  name: String,
  columns: List[Column],
  comment: Option[String] = None,
  annotations: List[TableAnnotation] = List(),
) extends NamedWithSchema
    with Columns

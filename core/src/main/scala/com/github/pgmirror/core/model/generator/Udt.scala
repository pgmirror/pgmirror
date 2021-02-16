package com.github.pgmirror.core.model.generator

case class Udt(
  schemaName: String,
  name: String,
  columns: List[Column],
  comment: Option[String] = None,
) extends NamedWithSchema
    with Columns

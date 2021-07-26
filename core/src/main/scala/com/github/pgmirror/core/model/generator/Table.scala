package com.github.pgmirror.core.model.generator

import com.github.pgmirror.core.util.Dag

case class Table(
  schemaName: String,
  name: String,
  columns: List[Column],
  comment: Option[String] = None,
  foreignKeys: List[ForeignKey] = List(),
  annotations: List[TableAnnotation] = List(),
) extends NamedWithSchema
    with Columns
    with Dag[Table] {
  override def dependencies: Iterable[Table] = foreignKeys.map(_.foreignTable)
}

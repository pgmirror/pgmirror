package com.github.pgmirror.core.model.generator

trait NamedWithSchema {
  val schemaName: String
  val name: String

  def nameWithSchema: String =
    List(s""""$schemaName"""", s""""$name"""").filterNot(_.isEmpty).mkString(".")
}

package com.github.irumiha.pgmirror

import java.util.regex.Pattern

import scala.util.matching.Regex

case class Settings(
  url: String,
  user: String,
  password: String,
  rootPath: String,
  rootPackage: String,
  // If you set schemas list then the schemaNameInclude will be ignored
  schemas: List[String] = List(),
  schemaNameInclude: Regex = "^public$".r,
  tableNameInclude: Regex = ".*".r,
  udtNameInclude: Regex = ".*".r,
  // A schema with this name will not generate a package
  defaultSchema: String = "public"
) {
  lazy val schemaFilter: Pattern = schemaNameInclude.pattern
  lazy val tableFilter: Pattern = tableNameInclude.pattern
  lazy val udtFilter: Pattern = udtNameInclude.pattern
}

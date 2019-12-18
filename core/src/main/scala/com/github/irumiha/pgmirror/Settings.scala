package com.github.irumiha.pgmirror

import java.util.regex.Pattern

import scala.util.matching.Regex

case class Settings(
  url: String,
  user: String,
  password: String,
  rootPath: String,
  rootPackage: String,
  schemaNameInclude: Regex = "^public$".r,
  schemaNameExclude: Regex = "^public$".r,
  tableNameInclude: Regex = ".*".r,
  udtNameInclude: Regex = ".*".r,
  defaultSchema: String = "public"
) {
  lazy val schemaFilter: Pattern = schemaNameInclude.pattern
  lazy val tableFilter: Pattern = tableNameInclude.pattern
  lazy val udtFilter: Pattern = udtNameInclude.pattern
}

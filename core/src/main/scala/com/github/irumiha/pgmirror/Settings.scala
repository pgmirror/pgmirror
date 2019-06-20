package com.github.irumiha.pgmirror

import scala.util.matching.Regex

case class Settings(
  url: String,
  user: String,
  password: String,
  rootPath: String,
  rootPackage: String,
  schemaNameInclude: Regex = "^public$".r,
  tableNameInclude: Regex = ".*".r,
  udtNameInclude: Regex = ".*".r,
  viewNameInclude: Regex = ".*".r
) {
  lazy val schemaFilter = schemaNameInclude.pattern
  lazy val tableFilter = tableNameInclude.pattern
  lazy val udtFilter = udtNameInclude.pattern
  lazy val viewFilter = viewNameInclude.pattern
}

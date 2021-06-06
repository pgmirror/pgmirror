package com.github.pgmirror.core

import java.util.regex.Pattern

import scala.util.matching.Regex

case class Settings(
  driverClass: String,
  url: String,
  user: String,
  password: String,
  rootPath: String,
  rootPackage: String,
  // If you set schemas then the schemaNameInclude will be ignored
  schemas: Set[String] = Set(),
  schemaNameInclude: Regex = "^.*$".r,
  tableNameInclude: Regex = ".*".r,
  udtNameInclude: Regex = ".*".r,
  // A schema with this name will not generate a package
  defaultSchema: String = "public",
  generateCirce: Boolean = false,
) {
  lazy val schemaFilter: Pattern = schemaNameInclude.pattern
  lazy val tableFilter: Pattern = tableNameInclude.pattern
  lazy val udtFilter: Pattern = udtNameInclude.pattern
}

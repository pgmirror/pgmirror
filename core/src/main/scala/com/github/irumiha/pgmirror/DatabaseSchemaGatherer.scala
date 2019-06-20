package com.github.irumiha.pgmirror

import java.sql.{Connection, DriverManager}

import com.github.irumiha.pgmirror.model.{Column, Database, Table, Udt}
import io.tmos.arm.ArmMethods._
import ResultSetIterator._
import SqlTypes._

abstract class DatabaseSchemaGatherer(settings: Settings) {

  protected lazy val driverCls: Class[_] = Class.forName("org.postgresql.Driver")
  protected lazy val database: Connection = DriverManager.getConnection(settings.url, settings.user, settings.password)

  def loadDatabase: Database = {
    val tableConstraints =
      """select constraint_schema, constraint_name, table_schema, table_name, constraint_type
        |from information_schema.table_constraints
        |""".stripMargin

    val referentialConstraints =
      """select constraint_schema, constraint_name, unique_constraint_schema, unique_constraint_name
        |from information_schema.referential_constraints
        |""".stripMargin

    val keyColumnUsage =
      """select constraint_schema, constraint_name, table_schema, table_name, column_name
        |from information_schema.key_column_usage
        |""".stripMargin

    val tables =
      """select table_schema, table_name, table_type, is_insertable_into
        |from information_schema.tables
        |""".stripMargin

    val columns =
      """select table_schema, table_name, column_name, ordinal_position, column_default, is_nullable, data_type, udt_schema, udt_name
        |from information_schema.columns
        |""".stripMargin

    val udtAttributes =
      """select udt_schema, udt_name, attribute_name, ordinal_position, is_nullable, data_type, attribute_udt_schema, attribute_udt_name
        |from information_schema.attributes
        |""".stripMargin

    Database(List(), List(), List())
  }
}

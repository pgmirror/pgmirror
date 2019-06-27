package com.github.irumiha.pgmirror

import java.sql.{Connection, DriverManager, ResultSet}

import com.github.irumiha.pgmirror.ResultSetIterator._
import com.github.irumiha.pgmirror.model.gatherer._
import com.github.irumiha.pgmirror.model.generator.{Column, Database, ForeignKey, Table, View}

abstract class DatabaseSchemaGatherer(settings: Settings) {

  protected lazy val driverCls: Class[_] = Class.forName("org.postgresql.Driver")
  protected lazy val database: Connection = DriverManager.getConnection(settings.url, settings.user, settings.password)

  def gatherDatabase: Database = {
    def runStatement[R](querySql: String, tr: ResultSet => R): List[R] = {
      val ps = database.prepareStatement(querySql)
      val list = ps.executeQuery().toIterator.map(tr).toList
      ps.close()
      list
    }

    val pgForeignKeys = runStatement(PgForeignKeys.sql, PgForeignKeys.fromResultSet)
    val pgTables = runStatement(PgTables.sql, PgTables.fromResultSet)
    val pgColumns = runStatement(PgColumns.sql, PgColumns.fromResultSet)
    val pgUdtAttributes = runStatement(PgUdtAttributes.sql, PgUdtAttributes.fromResultSet)
    val pgBaseTables = pgTables.filter(_.tableType == "BASE TABLE")
    val pgViews = pgTables.filter(_.tableType == "VIEW")

    val tables = pgBaseTables.map{ pgt =>
      val columns = pgColumns.filter(p => p.tableSchema == pgt.tableSchema && p.tableName == pgt.tableName).map(pgc =>
        Column(
          name = pgc.columnName,
          columnType = pgc.dataType,
          typeName = s"${pgc.udtSchema}.${pgc.udtName}",
          modelType = "",
          isNullable = pgc.isNullable,
          isPrimaryKey = pgc.isPrimaryKey,
          comment = if (pgc.description.nonEmpty) Some(pgc.description) else None
        )
      )

      Table(
        schemaName = pgt.tableSchema,
        tableName = pgt.tableName,
        columns = columns,
        comment = if (pgt.description.nonEmpty) Some(pgt.description) else None,
        foreignKeys = List()
      )
    }

    val views = pgViews.map{ pgt =>
      val columns = pgColumns.filter(p => p.tableSchema == pgt.tableSchema && p.tableName == pgt.tableName).map(pgc =>
        Column(
          name = pgc.columnName,
          columnType = pgc.dataType,
          typeName = s"${pgc.udtSchema}.${pgc.udtName}",
          modelType = "",
          isNullable = pgc.isNullable,
          isPrimaryKey = pgc.isPrimaryKey,
          comment = if (pgc.description.nonEmpty) Some(pgc.description) else None
        )
      )

      View(
        schemaName = pgt.tableSchema,
        viewName = pgt.tableName,
        columns = columns,
        comment= if (pgt.description.nonEmpty) Some(pgt.description) else None,
        isUpdatable = false,
        isInsertable = pgt.isInsertableInto
      )
    }

    Database(List(), List(), List())
  }
}

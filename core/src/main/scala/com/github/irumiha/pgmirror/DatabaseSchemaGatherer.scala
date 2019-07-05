package com.github.irumiha.pgmirror

import java.sql.{Connection, DriverManager, ResultSet}

import com.github.irumiha.pgmirror.ResultSetIterator._
import com.github.irumiha.pgmirror.model.gatherer._
import com.github.irumiha.pgmirror.model.generator.{Column, Database, ForeignKey, Table, TableLike, Udt, View}

class DatabaseSchemaGatherer(settings: Settings) {

  protected lazy val driverCls: Class[_] = Class.forName("org.postgresql.Driver")
  protected lazy val database: Connection = DriverManager.getConnection(settings.url, settings.user, settings.password)

  def gatherDatabase: Either[List[Throwable], Database] = {
    def runStatement[R](querySql: String, tr: ResultSet => R): List[R] = {
      val ps = database.prepareStatement(querySql)
      val list = ps.executeQuery().toIterator.map(tr).toList
      ps.close()
      list
    }

    val pgForeignKeys = runStatement(PgForeignKeys.sql, PgForeignKeys.fromResultSet)
      .filter(f => settings.schemaFilter.matcher(f.tableSchema).matches())
    val pgTables = runStatement(PgTables.sql, PgTables.fromResultSet)
      .filter(t => settings.schemaFilter.matcher(t.tableSchema).matches() &&
                   settings.tableFilter.matcher(t.tableName).matches())
    val pgColumns = runStatement(PgColumns.sql, PgColumns.fromResultSet)
      .filter(t => settings.schemaFilter.matcher(t.tableSchema).matches() &&
                   settings.tableFilter.matcher(t.tableName).matches())

    // TODO implement UDTs
    // val pgUdtAttributes = runStatement(PgUdtAttributes.sql, PgUdtAttributes.fromResultSet)

    val columns = pgColumns.map { pgc =>
      val schema = if (pgc.udtSchema == settings.defaultSchema) "" else pgc.udtSchema
      SqlTypes.typeMapping(schema, pgc.udtName, pgc.dataType).map(dt =>
        Column(
          tableSchema = pgc.tableSchema,
          tableName = pgc.tableName,
          name = pgc.columnName,
          columnType = pgc.dataType,
          typeName = s"${pgc.udtSchema}.${pgc.udtName}",
          modelType = dt,
          isNullable = pgc.isNullable,
          isPrimaryKey = pgc.isPrimaryKey,
          ordinalPosition = pgc.ordinalPosition,
          comment = if (pgc.description.nonEmpty) Some(pgc.description) else None
        )
      )
    }
    val allErrors = columns.collect { case Left(err) => err }
    if (allErrors.nonEmpty) {
      Left(allErrors)
    } else {
      val goodColumns = columns.collect { case Right(c) => c }
      val tablesAndViews =
        pgTables.map { pgt =>
          TableLike(
            tableType = if (pgt.tableType == "BASE TABLE") Table else if (pgt.tableType == "VIEW") View else Udt,
            schemaName = pgt.tableSchema,
            tableName = pgt.tableName,
            tableClassName = pgt.tableName.split("_").filterNot(_.isEmpty).map(_.capitalize).mkString,
            columns = goodColumns.filter(c => c.tableSchema == pgt.tableSchema && c.tableName == pgt.tableName),
            comment = if (pgt.description.nonEmpty) Some(pgt.description) else None,
            foreignKeys = List()
          )
        }

      val tables = tablesAndViews.filter(_.tableType == Table)
      val views  = tablesAndViews.filter(_.tableType == View)
      val udts   = tablesAndViews.filter(_.tableType == Udt)

      val tableFKs =
        for {
          fgk          <- pgForeignKeys
          table        <- tables.find(t => t.schemaName == fgk.tableSchema && t.tableName == fgk.tableName).toList
          col          <- table.columns.find(_.name == fgk.columnName).toList
          foreignTable <- tables.find(t => t.schemaName == fgk.foreignTableSchema && t.tableName == fgk.foreignTableName).toList
          foreignCol   <- foreignTable.columns.find(_.name == fgk.foreignColumnName).toList
        } yield
          ForeignKey(
            table,
            col,
            foreignTable,
            foreignCol
          )

      Right(Database(tables, views, udts, tableFKs))
    }
  }
}

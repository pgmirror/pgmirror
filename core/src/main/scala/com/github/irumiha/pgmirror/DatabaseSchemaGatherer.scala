package com.github.irumiha.pgmirror

import java.sql.{Connection, DriverManager, ResultSet}

import com.github.irumiha.pgmirror.ResultSetIterator._
import com.github.irumiha.pgmirror.model.gatherer._
import com.github.irumiha.pgmirror.model.generator.{Column, ColumnAnnotation, Database, ForeignKey, Table, TableAnnotation, TableLike, Udt, View}

class DatabaseSchemaGatherer(settings: Settings) {

  private val driverCls: Class[_] = Class.forName("org.postgresql.Driver")
  protected lazy val database: Connection = DriverManager.getConnection(settings.url, settings.user, settings.password)

  private def columnAnnotations(comment: Option[String]): Set[ColumnAnnotation] =
    ColumnAnnotation.values.flatMap { a =>
      val mi = a.regex.findAllIn(comment.getOrElse(""))
      for (_ <- mi) yield {
        a match {
          case ColumnAnnotation.Command(_) => ColumnAnnotation.Command(mi.group("commandname"))
          case _ => a
        }
      }
    }.toSet

  private def tableAnnotations(comment: Option[String]): Set[TableAnnotation] = {
    TableAnnotation.values.flatMap { a =>
      for (_ <- a.regex.findAllIn(comment.getOrElse(""))) yield a
    }
  }.toSet

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
      val tableSchema = if (pgc.tableSchema == settings.defaultSchema) "" else pgc.tableSchema

      SqlTypes.typeMapping(schema, pgc.udtName, pgc.dataType).map(dt =>
        Column(
          tableSchema = tableSchema,
          tableName = pgc.tableName,
          name = pgc.columnName,
          columnType = pgc.dataType,
          typeName = s"${pgc.udtSchema}.${pgc.udtName}",
          modelType = dt,
          isNullable = pgc.isNullable,
          isPrimaryKey = pgc.isPrimaryKey,
          ordinalPosition = pgc.ordinalPosition,
          comment = pgc.description.filterNot(_.isEmpty),
          annotations = columnAnnotations(pgc.description).toList
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
          val tableSchema = if (pgt.tableSchema == settings.defaultSchema) "" else pgt.tableSchema
          TableLike(
            tableType = if (pgt.tableType == "BASE TABLE") Table else if (pgt.tableType == "VIEW") View else Udt,
            schemaName = tableSchema,
            tableName = pgt.tableName,
            tableClassName = pgt.tableName.split("_").filterNot(_.isEmpty).map(_.capitalize).mkString,
            columns = goodColumns.filter(c => c.tableSchema == tableSchema && c.tableName == pgt.tableName),
            comment = pgt.description.filterNot(_.isEmpty),
            foreignKeys = List(),
            annotations = tableAnnotations(pgt.description).toList
          )
        }

      val tables = tablesAndViews.filter(_.tableType == Table)
      val views  = tablesAndViews.filter(_.tableType == View)
      val udts   = tablesAndViews.filter(_.tableType == Udt)

      val tableFKs =
        for {
          fgk          <- pgForeignKeys
          fkTableSchema = if (fgk.tableSchema == settings.defaultSchema) "" else fgk.tableSchema
          fkForeignTableSchema = if (fgk.foreignTableSchema == settings.defaultSchema) "" else fgk.foreignTableSchema
          table        <- tables.find(t => t.schemaName == fkTableSchema && t.tableName == fgk.tableName).toList
          col          <- table.columns.find(_.name == fgk.columnName).toList
          foreignTable <- tables.find(t => t.schemaName == fkForeignTableSchema && t.tableName == fgk.foreignTableName).toList
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

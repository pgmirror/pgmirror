package com.github.pgmirror.core

import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet}

import com.github.pgmirror.core.model.gatherer.{PgColumns, PgForeignKeys, PgTables}
import com.github.pgmirror.core.model.generator.{Column, ColumnAnnotation, Database, ForeignKey, Table, TableAnnotation, TableLike, Udt, View}

class DatabaseSchemaGatherer(settings: Settings) {

  private val driverCls: Class[_] = Class.forName("org.postgresql.Driver")
  protected lazy val database: Connection = DriverManager.getConnection(settings.url, settings.user, settings.password)

  private def columnAnnotations(comment: Option[String]): Set[ColumnAnnotation] =
    ColumnAnnotation.values.filter(_.regex.findAllIn(comment.getOrElse("")).nonEmpty).toSet

  private def tableAnnotations(comment: Option[String]): Set[TableAnnotation] = {
    TableAnnotation.values.flatMap { a =>
      for (_ <- a.regex.findAllIn(comment.getOrElse(""))) yield a
    }
  }.toSet

  def gatherDatabase: Either[List[Throwable], Database] = {
    import com.github.pgmirror.core.ResultSetIterator._

    def runStatement[R](ps: PreparedStatement, tr: ResultSet => R): List[R] = {
      val list = ps.executeQuery().toIterator.map(tr).toList
      ps.close()
      list
    }

    val pgForeignKeys =
      runStatement(database.prepareStatement(PgForeignKeys.sql), PgForeignKeys.fromResultSet)
      .filter(f => settings.schemaFilter.matcher(f.tableSchema).matches())

    val rawTables =
      if (settings.schemas.nonEmpty) {
        val ps = database.prepareStatement(PgTables.sqlSchemaInList(settings.schemas))
        settings.schemas.zipWithIndex.foreach { case (schema, i) => ps.setString(i+1, schema)}
        runStatement(ps, PgTables.fromResultSet)
      } else {
        runStatement(database.prepareStatement(PgTables.sql), PgTables.fromResultSet)
      }

    val pgTables =
      rawTables.filter(t => settings.schemaFilter.matcher(t.tableSchema).matches() &&
                            settings.tableFilter.matcher(t.tableName).matches())

    val pgColumns = runStatement(database.prepareStatement(PgColumns.sql), PgColumns.fromResultSet)
      .filter(t => settings.schemaFilter.matcher(t.tableSchema).matches() &&
                   settings.tableFilter.matcher(t.tableName).matches())


    // TODO implement Enums (maybe)
    // val pgEnums = runStatement(database.prepareStatement(PgEnums.sql), PgEnums.fromResultSet)
    //   .filter(t => settings.schemaFilter.matcher(t.enumSchema).matches())

    // TODO implement UDTs
    // val pgUdtAttributes = runStatement(PgUdtAttributes.sql, PgUdtAttributes.fromResultSet)

    val columns = pgColumns.map { pgc =>
      val schema = if (pgc.udtSchema == settings.defaultSchema) "" else pgc.udtSchema
      val tableSchema = if (pgc.tableSchema == settings.defaultSchema) "" else pgc.tableSchema

      SqlTypes.typeMapping(schema, pgc.udtName, pgc.dataType).map { dt =>
        val annotations = columnAnnotations(pgc.description)

        Column(
          tableSchema = tableSchema,
          tableName = pgc.tableName,
          name = pgc.columnName,
          columnType = pgc.dataType,
          typeName = s"${pgc.udtSchema}.${pgc.udtName}",
          modelType = dt,
          isNullable = pgc.isNullable && !annotations.contains(ColumnAnnotation.NotNull),
          isPrimaryKey = pgc.isPrimaryKey,
          ordinalPosition = pgc.ordinalPosition,
          comment = pgc.description.filterNot(_.isEmpty),
          annotations = annotations.toList
        )
      }
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
            name = pgt.tableName,
            className = pgt.tableName.split("_").filterNot(_.isEmpty).map(_.capitalize).mkString,
            columns = goodColumns.filter(c => c.tableSchema == tableSchema && c.tableName == pgt.tableName),
            comment = pgt.description.filterNot(_.isEmpty),
            foreignKeys = List(),
            annotations = tableAnnotations(pgt.description).toList,
            isView = pgt.tableType == "VIEW",
            isInsertable = pgt.tableType == "BASE TABLE"
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
          table        <- tables.find(t => t.schemaName == fkTableSchema && t.name == fgk.tableName).toList
          col          <- table.columns.find(_.name == fgk.columnName).toList
          foreignTable <- tables.find(t => t.schemaName == fkForeignTableSchema && t.name == fgk.foreignTableName).toList
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

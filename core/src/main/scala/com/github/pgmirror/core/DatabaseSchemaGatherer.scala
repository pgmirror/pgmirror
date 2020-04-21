package com.github.pgmirror.core

import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet}

import com.github.pgmirror.core.model.gatherer.{PgColumn, PgForeignKey, PgTable}
import com.github.pgmirror.core.model.generator.{
  Column,
  ColumnAnnotation,
  Database,
  ForeignKey,
  Table,
  TableAnnotation,
  TableLike,
  Udt,
  View
}
import com.github.pgmirror.core.ResultSetIterator._

class DatabaseSchemaGatherer(settings: Settings) {

  private val driverCls: Class[_] = Class.forName("org.postgresql.Driver")
  protected lazy val database: Connection =
    DriverManager.getConnection(settings.url, settings.user, settings.password)

  /**
    * The default schema is something we don't create a package for.
    * For example, that allows us to not create a package called "public".
    *
    * @param schema
    * @return
    */
  private def schemaOrEmpty(schema: String) =
    if (schema == settings.defaultSchema) "" else schema

  private def findTable(tables: List[TableLike], schema: String, name: String) =
    tables
      .find(t => t.schemaName == schema && t.name == name)

  private def findCol(table: TableLike, name: String) =
    table.columns.find(_.name == name)

  private def columnIsNullable(
    pgc: PgColumn,
    annotations: List[ColumnAnnotation]
  ) = {
    pgc.isNullable && !annotations.contains(ColumnAnnotation.NotNull)
  }

  private def extractTablesAndViews(
    pgTables: List[PgTable],
    goodColumns: List[Column]
  ) = {
    pgTables.map { pgt =>
      val tableSchema = schemaOrEmpty(pgt.tableSchema)

      TableLike(
        tableType =
          if (pgt.tableType == "BASE TABLE") Table
          else if (pgt.tableType == "VIEW") View
          else Udt,
        schemaName = tableSchema,
        name = pgt.tableName,
        className = pgt.tableName
          .split("_")
          .filterNot(_.isEmpty)
          .map(_.capitalize)
          .mkString,
        columns = goodColumns.filter(c =>
          c.tableSchema == tableSchema && c.tableName == pgt.tableName
        ),
        comment = pgt.description.filterNot(_.isEmpty),
        foreignKeys = List(),
        annotations = TableAnnotation.findAllFor(pgt),
        isView = pgt.tableType == "VIEW",
        isInsertable = pgt.tableType == "BASE TABLE"
      )
    }
  }

  private def runStatement[R](
    ps: PreparedStatement,
    tr: ResultSet => R
  ): List[R] = {
    val list = ps.executeQuery().toIterator.map(tr).toList
    ps.close()
    list
  }

  private def getPgColumns(
    detectedSchemas: Set[String],
    detectedTables: Set[String]
  ) = {
    val pgColumns = runStatement(
      database.prepareStatement(PgColumn.sql),
      PgColumn.fromResultSet
    ).filter(t =>
      detectedSchemas.contains(t.tableSchema) && detectedTables.contains(
        t.tableName
      )
    )
    pgColumns
  }

  private def getPgTables = {
    if (settings.schemas.nonEmpty) {
      val ps =
        database.prepareStatement(PgTable.sqlSchemaInList(settings.schemas))
      settings.schemas.zipWithIndex.foreach {
        case (schema, i) => ps.setString(i + 1, schema)
      }

      runStatement(ps, PgTable.fromResultSet)
    } else {
      runStatement(
        database.prepareStatement(PgTable.sql),
        PgTable.fromResultSet
      ).filter(t =>
        settings.schemaFilter.matcher(t.tableSchema).matches() &&
          settings.tableFilter.matcher(t.tableName).matches()
      )
    }
  }

  private def getPgForeignKeys = {
    val pgForeignKeys =
      runStatement(
        database.prepareStatement(PgForeignKey.sql),
        PgForeignKey.fromResultSet
      ).filter(f => settings.schemaFilter.matcher(f.tableSchema).matches())
    pgForeignKeys
  }

  def gatherDatabase: Either[List[Throwable], Database] = {

    val pgTables = getPgTables

    val detectedSchemas = pgTables.map(_.tableSchema).toSet
    val detectedTables = pgTables.map(_.tableName).toSet

    val pgColumns = getPgColumns(detectedSchemas, detectedTables)

    val columns = pgColumns.map { pgc =>
      val schema = schemaOrEmpty(pgc.udtSchema)
      val tableSchema = schemaOrEmpty(pgc.tableSchema)

      SqlTypes
        .typeMapping(settings.rootPackage, schema, pgc.udtName, pgc.dataType)
        .map { dt =>
          val annotations = ColumnAnnotation.findAllFor(pgc)
          Column(
            tableSchema = tableSchema,
            tableName = pgc.tableName,
            name = pgc.columnName,
            columnType = pgc.dataType,
            typeName = s"${pgc.udtSchema}.${pgc.udtName}",
            modelType = dt.modelType,
            isNullable = columnIsNullable(pgc, annotations),
            isPrimaryKey = pgc.isPrimaryKey,
            ordinalPosition = pgc.ordinalPosition,
            comment = pgc.description.filterNot(_.isEmpty),
            annotations = annotations,
            hasDefault = !pgc.columnDefault.isBlank
          )
        }
    }

    val allErrors = columns.collect { case Left(err) => err }

    if (allErrors.nonEmpty) {
      Left(allErrors)
    } else {
      val goodColumns = columns.collect { case Right(c) => c }
      val tablesAndViews =
        extractTablesAndViews(pgTables, goodColumns)

      val tables = tablesAndViews.filter(_.tableType == Table)
      val views = tablesAndViews.filter(_.tableType == View)
      val udts = tablesAndViews.filter(_.tableType == Udt)

      val tableFKs = getPgForeignKeys.flatMap { fk =>
        val fkTableSchema = schemaOrEmpty(fk.tableSchema)
        val fkForeignTableSchema = schemaOrEmpty(fk.foreignTableSchema)

        for {
          table <- findTable(tables, fkTableSchema, fk.tableName)
          col <- findCol(table, fk.columnName)
          foreignTable <-
            findTable(tables, fkForeignTableSchema, fk.foreignTableName)
          foreignCol <- findCol(foreignTable, fk.foreignColumnName)
        } yield ForeignKey(
          table,
          col,
          foreignTable,
          foreignCol
        )
      }

      Right(Database(tables, views, udts, tableFKs))
    }
  }
}

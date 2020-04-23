package com.github.pgmirror.core

import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet}

import com.github.pgmirror.core.ResultSetIterator._
import com.github.pgmirror.core.model.gatherer.{PgColumn, PgForeignKey, PgTable}
import com.github.pgmirror.core.model.generator._

class DatabaseSchemaGatherer(settings: Settings) {

  protected lazy val database: Connection =
    DriverManager.getConnection(settings.url, settings.user, settings.password)
  private val driverCls: Class[_] = Class.forName("org.postgresql.Driver")

  def gatherDatabase: Either[List[Throwable], Database] = {

    val pgTables = getPgTables

    val detectedSchemas = pgTables.map(_.tableSchema).toSet
    val detectedTables = pgTables.map(_.tableName).toSet

    val pgColumns = getPgColumns(detectedSchemas, detectedTables)

    for {
      columns <- extractColumns(pgColumns)
    } yield extractIntoDatabase(pgTables, columns)
  }

  private def getPgColumns(
    detectedSchemas: Set[String],
    detectedTables: Set[String],
  ) = {
    val pgColumns = runStatement(
      database.prepareStatement(PgColumn.sql),
      PgColumn.fromResultSet,
    ).filter(t =>
      detectedSchemas.contains(t.tableSchema) && detectedTables.contains(
        t.tableName,
      ),
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
        PgTable.fromResultSet,
      ).filter(t =>
        settings.schemaFilter.matcher(t.tableSchema).matches() &&
          settings.tableFilter.matcher(t.tableName).matches(),
      )
    }
  }

  private def extractColumns(
    pgColumns: List[PgColumn],
  ): Either[List[Throwable], List[Column]] = {
    val columns = pgColumns.map { postgresColumn =>
      val schema = schemaOrEmpty(postgresColumn.udtSchema)
      val tableSchema = schemaOrEmpty(postgresColumn.tableSchema)

      SqlTypes
        .typeMapping(settings.rootPackage, schema, postgresColumn.udtName, postgresColumn.dataType)
        .map { dt =>
          val annotations = ColumnAnnotation.findAllFor(postgresColumn)
          Column(
            tableSchema = tableSchema,
            tableName = postgresColumn.tableName,
            name = postgresColumn.columnName,
            columnType = postgresColumn.dataType,
            typeName = s"${postgresColumn.udtSchema}.${postgresColumn.udtName}",
            modelType = dt.modelType,
            isNullable = columnIsNullable(postgresColumn, annotations),
            isPrimaryKey = postgresColumn.isPrimaryKey,
            ordinalPosition = postgresColumn.ordinalPosition,
            comment = postgresColumn.description.filterNot(_.isEmpty),
            annotations = annotations,
            hasDefault = !postgresColumn.columnDefault.isBlank,
          )
        }
    }

    columns.partition(_.isLeft) match {
      case (Nil, columns) => Right(for (Right(i) <- columns) yield i)
      case (errors, _)    => Left(for (Left(s) <- errors) yield s)
    }
  }

  /**
    * The default schema is something we don't create a package for.
    * For example, that allows us to not create a package called "public".
    *
    * @param schema
    * @return
    */
  private def schemaOrEmpty(schema: String) =
    if (schema == settings.defaultSchema) "" else schema

  private def columnIsNullable(
    pgc: PgColumn,
    annotations: List[ColumnAnnotation],
  ) = {
    pgc.isNullable && !annotations.contains(ColumnAnnotation.NotNull)
  }

  private def extractIntoDatabase(pgTables: List[PgTable], allColumns: List[Column]) = {
    val (tables, views, udts) =
      extractTablesAndViews(pgTables, allColumns)

    val tableFKs = extractForeignKeys(tables)

    Database(tables, views, udts, tableFKs)
  }

  private def extractTablesAndViews(
    pgTables: List[PgTable],
    goodColumns: List[Column],
  ): (List[Table], List[View], List[Udt]) = {
    pgTables.foldRight((List[Table](), List[View](), List[Udt]())) { (pgt, r) =>
      val tableSchema = schemaOrEmpty(pgt.tableSchema)

      val t = TableLike(
        schemaName = tableSchema,
        name = pgt.tableName,
        className = Names.camelCaseize(pgt.tableName),
        columns =
          goodColumns.filter(c => c.tableSchema == tableSchema && c.tableName == pgt.tableName),
        comment = pgt.description.filterNot(_.isEmpty),
        foreignKeys = List(),
        annotations = TableAnnotation.findAllFor(pgt),
        isInsertable = pgt.tableType == "BASE TABLE",
      )

      if (pgt.tableType == "BASE TABLE") {
        (Table(t) +: r._1, r._2, r._3)
      } else if (pgt.tableType == "VIEW") {
        (r._1, View(t) +: r._2, r._3)
      } else {
        (r._1, r._2, Udt(t) +: r._3)
      }
    }
  }

  private def extractForeignKeys(tables: List[Table]) = {
    runStatement(
      database.prepareStatement(PgForeignKey.sql),
      PgForeignKey.fromResultSet,
    ).filter(f => settings.schemaFilter.matcher(f.tableSchema).matches()).flatMap { fk =>
      val fkTableSchema = schemaOrEmpty(fk.tableSchema)
      val fkForeignTableSchema = schemaOrEmpty(fk.foreignTableSchema)

      for {
        table <- findTable(tables, fkTableSchema, fk.tableName)
        col <- findCol(table, fk.columnName)
        foreignTable <- findTable(tables, fkForeignTableSchema, fk.foreignTableName)
        foreignCol <- findCol(foreignTable, fk.foreignColumnName)
      } yield ForeignKey(
        table,
        col,
        foreignTable,
        foreignCol,
      )
    }
  }

  private def findTable(tables: List[Table], schema: String, name: String) =
    tables
      .find(t => t.value.schemaName == schema && t.value.name == name)

  private def findCol(table: Table, name: String) =
    table.value.columns.find(_.name == name)

  private def runStatement[R](
    ps: PreparedStatement,
    tr: ResultSet => R,
  ): List[R] = {
    val list = ps.executeQuery().toIterator.map(tr).toList
    ps.close()
    list
  }
}

package com.github.pgmirror.core.model.generator

import com.github.pgmirror.core.Settings
import com.github.pgmirror.core.model.database

import java.sql.Connection
import scala.collection.mutable.ListBuffer
import scala.util.Try

case class Database(
  tables: List[Table],
  views: List[View],
  udts: List[Udt],
  foreignKeys: List[ForeignKey],
)

object Database {
  def build(settings: Settings, connection: Connection): Try[Database] = Try {
    val (tables, views, foreignKeys) = getAllTables(settings, connection)

    Database(
      tables = tables,
      views = views,
      udts = List(),
      foreignKeys = foreignKeys
    )
  }

  private def getAllTables(settings: Settings, connection: Connection): (List[Table], List[View], List[ForeignKey]) = {
    val tables = database.Table
      .getTables(connection)
      .filter(tableBySchema(settings))
      .filter(t => settings.tableFilter.matcher(t.tableName).matches())
      .map { t =>
        val dbColumns = database.Column.getColumns(connection, t.tableSchema, t.tableName)
        val pks = database.PrimaryKey.getPrimaryKeys(connection, t.tableSchema, t.tableName)

        val pkSet = pks.map(_.columnName).toSet

        val generatorColumns = dbColumns.map { dbc =>
          Column(
            tableSchema = dbc.tableSchema,
            tableName = dbc.tableName,
            name = dbc.columnName,
            columnType = dbc.dataTypeName,
            isNullable = dbc.isNullable,
            isPrimaryKey = pkSet.contains(dbc.columnName),
            ordinalPosition = dbc.ordinalPosition,
            comment = dbc.description,
            annotations = ColumnAnnotation.findAllForDbColumn(dbc),
            hasDefault = dbc.columnDefault.nonEmpty,
          )
        }

        if (t.tableType == "TABLE") {
          (Some(Table(
            schemaName = t.tableSchema,
            name = t.tableName,
            columns = generatorColumns,
            comment = t.description,
            annotations = TableAnnotation.findAllForDbTable(t),
          )), None)
        } else {
          (None, Some(View(
            schemaName = t.tableSchema,
            name = t.tableName,
            columns = generatorColumns,
            comment = t.description,
            annotations = TableAnnotation.findAllForDbTable(t)
          )))
        }
      }
    val onlyTables = tables.filter(_._1.isDefined).map(_._1.get)
    val onlyViews = tables.filter(_._2.isDefined).map(_._2.get)
    val foreignKeys: ListBuffer[ForeignKey] = ListBuffer()

    val tablesWithFKs = onlyTables.map{ t =>
      val dbFKs = database.ForeignKey.getForTable(connection, t.schemaName, t.name)
      def findForeignTable(schema: String, name: String): Table =
        onlyTables.find(fkt => fkt.schemaName == schema && fkt.name == name).get

      t.copy(foreignKeys = dbFKs.map{ dbfk =>
        val ft = findForeignTable(dbfk.foreignTableSchema, dbfk.foreignTableName)
        val ftc = ft.columns.find(_.name == dbfk.foreignColumnName).get

        val theFk = ForeignKey(t, t.columns.find(_.name == dbfk.columnName).get, ft, ftc)
        foreignKeys += theFk

        theFk
      })
    }

    (tablesWithFKs, onlyViews, foreignKeys.toList)
  }

  def tableBySchema(settings: Settings): database.Table => Boolean = { t =>
    if (settings.schemas.nonEmpty) {
      settings.schemas.contains(t.tableSchema)
    } else {
      settings.schemaFilter.matcher(t.tableSchema).matches()
    }
  }

}

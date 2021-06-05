package com.github.pgmirror.core.model.generator

import com.github.pgmirror.core.Settings
import com.github.pgmirror.core.model.database

import java.sql.Connection
import scala.util.Try

case class Database(
  tables: List[Table],
  views: List[View],
  udts: List[Udt],
  foreignKeys: List[ForeignKey],
)

object Database {
  def build(settings: Settings, connection: Connection): Try[Database] = Try {
    val tables = database.Table
      .getTables(connection)
      .filter(tableBySchema(settings))
      .filter(t => settings.tableFilter.matcher(t.tableName).matches())
      .map { t =>
        val dbColumns = database.Column.getColumns(connection, t.tableSchema, t.tableName)
        val dbFks = database.ForeignKey.getForTable(connection, t.tableSchema, t.tableName)

        val generatorColumns = dbColumns.map { dbc =>
          Column(
            tableSchema = dbc.tableSchema,
            tableName = dbc.tableName,
            name = dbc.columnName,
            columnType = dbc.dataTypeName,
            isNullable = dbc.isNullable,
            isPrimaryKey = dbc.isPrimaryKey,
            ordinalPosition = dbc.ordinalPosition,
            comment = dbc.description,
            annotations = ColumnAnnotation.findAllForDbColumn(dbc),
            hasDefault = dbc.columnDefault.nonEmpty,
          )
        }

      }

    ???
  }

  def tableBySchema(settings: Settings): database.Table => Boolean = { t =>
    if (settings.schemas.nonEmpty) {
      settings.schemas.contains(t.tableSchema)
    } else {
      settings.schemaFilter.matcher(t.tableSchema).matches()
    }
  }
}

package com.github.pgmirror.core.model.database

import java.sql.{Connection, ResultSet}
import scala.collection.mutable

case class Column(
  tableSchema: String,
  tableName: String,
  columnName: String,
  ordinalPosition: Int,
  columnDefault: String,
  isNullable: Boolean,
  isPrimaryKey: Boolean,
  dataType: Int,
  dataTypeName: String,
  description: Option[String],
)

object Column {
  def getColumns(connection: Connection, schema: String, tableName: String): List[Column] = {
    val columnsList = mutable.ListBuffer[Column]()
    val columnsRs = connection.getMetaData.getColumns(null, schema, tableName, null)
    try {
      while (columnsRs.next()) {
        columnsList.append(fromResultSet(columnsRs))
      }
    } finally {
      columnsRs.close()
    }
    columnsList.toList
  }

  private def fromResultSet(rs: ResultSet): Column =
    Column(
      tableSchema = rs.getString("TABLE_SCHEM"),
      tableName = rs.getString("TABLE_NAME"),
      columnName = rs.getString("COLUMN_NAME"),
      ordinalPosition = rs.getInt("ORDINAL_POSITION"),
      columnDefault = Option(rs.getString("COLUMN_DEF")).getOrElse(""),
      isNullable = rs.getString("IS_NULLABLE") == "YES",
      dataType = rs.getInt("DATA_TYPE"),
      dataTypeName = rs.getString("TYPE_NAME"),
      isPrimaryKey = false,
      description = Option(rs.getString("REMARKS")),
    )

}

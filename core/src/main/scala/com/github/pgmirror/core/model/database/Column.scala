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
  def fromResultSet(rs: ResultSet): Column =
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

  def getColumns(connection: Connection, tables: List[Table]): Map[Table, List[Column]] = {
    val allColumns = mutable.HashMap[Table, List[Column]]()
    tables.foreach { t =>
      val columnsList = mutable.ListBuffer[Column]()
      val columnsRs = connection.getMetaData.getColumns(null, t.tableSchema, t.tableName, null)
      try {
        while (columnsRs.next()) {
          columnsList.append(fromResultSet(columnsRs))
        }
      } finally {
        columnsRs.close()
      }
      allColumns.put(t, columnsList.toList)
    }

    allColumns.toMap
  }
}

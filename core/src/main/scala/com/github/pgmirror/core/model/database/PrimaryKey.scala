package com.github.pgmirror.core.model.database

import java.sql.Connection
import scala.collection.mutable

case class PrimaryKey(
  tableSchema: String,
  tableName: String,
  columnName: String,
  keySeq: Int,
)

object PrimaryKey {
  def getPrimaryKeys(connection: Connection, tableSchema: String, tableName: String): List[PrimaryKey] = {
    val pksRs = connection.getMetaData.getPrimaryKeys(null, tableSchema, tableName)
    try {
      val res = mutable.ListBuffer[PrimaryKey]()
      while (pksRs.next()) {
        res.append(
          PrimaryKey(
            tableSchema = pksRs.getString("TABLE_SCHEM"),
            tableName = pksRs.getString("TABLE_NAME"),
            columnName = pksRs.getString("COLUMN_NAME"),
            keySeq = pksRs.getInt("KEY_SEQ"),
          ),
        )
      }
      res.toList
    } finally {
      pksRs.close()
    }
  }
}

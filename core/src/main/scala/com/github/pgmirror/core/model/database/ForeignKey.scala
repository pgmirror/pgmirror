package com.github.pgmirror.core.model.database

import java.sql.{Connection, ResultSet}
import scala.collection.mutable

case class ForeignKey(
  tableSchema: String,
  tableName: String,
  columnName: String,
  foreignTableSchema: String,
  foreignTableName: String,
  foreignColumnName: String,
  keySeq: Int,
  fkName: String,
  pkName: String,
)

object ForeignKey {
  def getForTable(schema: String, table: String)(conn: Connection): List[ForeignKey] = {
    val importedKeysRs = conn.getMetaData.getImportedKeys(null, schema, table)
    try {
      val res = mutable.ListBuffer[ForeignKey]()
      while (importedKeysRs.next()) {
        res.append(fromResultSet(importedKeysRs))
      }
      res.toList
    } finally {
      importedKeysRs.close()
    }
  }

  def fromResultSet(rs: ResultSet): ForeignKey = {
    ForeignKey(
      tableSchema = rs.getString("FKTABLE_SCHEM"),
      tableName = rs.getString("FKTABLE_NAME"),
      columnName = rs.getString("FKCOLUMN_NAME"),
      foreignTableSchema = rs.getString("PKTABLE_SCHEM"),
      foreignTableName = rs.getString("PKTABLE_NAME"),
      foreignColumnName = rs.getString("PKCOLUMN_NAME"),
      keySeq = rs.getInt("KEY_SEQ"),
      fkName = rs.getString("FK_NAME"),
      pkName = rs.getString("PK_NAME"),
    )
  }
}

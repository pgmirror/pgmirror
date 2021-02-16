package com.github.pgmirror.core.model.database

import java.sql.{Connection, ResultSet}
import scala.collection.mutable

case class Table(
  tableSchema: String,
  tableName: String,
  tableType: String,
  description: Option[String],
)

object Table {
  def getTables(connection: Connection): List[Table] = {
    val tableTypes = getTableTypes(connection).intersect(List("VIEW", "TABLE")).toArray
    val tablesRs = connection.getMetaData.getTables(null, null, null, tableTypes)
    try {
      val res = mutable.ListBuffer[Table]()
      while (tablesRs.next()) {
        res.append(fromResultSet(tablesRs))
      }
      res.toList
    } finally {
      tablesRs.close()
    }
  }

  private def fromResultSet(rs: ResultSet): Table =
    Table(
      tableSchema = rs.getString("TABLE_SCHEM"),
      tableName = rs.getString("TABLE_NAME"),
      tableType = rs.getString("TABLE_TYPE"),
      description = Option(rs.getString("REMARKS")),
    )

  private def getTableTypes(connection: Connection) = {
    val tableTypesRs = connection.getMetaData.getTableTypes
    try {
      val tableTypes = mutable.ListBuffer[String]()
      while (tableTypesRs.next()) {
        tableTypes.append(tableTypesRs.getString("TABLE_TYPE"))
      }
      tableTypes.toList
    } finally {
      tableTypesRs.close()
    }
  }
}

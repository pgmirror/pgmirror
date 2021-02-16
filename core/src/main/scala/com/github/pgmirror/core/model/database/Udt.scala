package com.github.pgmirror.core.model.database

import java.sql.{Connection, ResultSet}
import scala.collection.mutable

case class Udt(
  udtSchema: String,
  udtName: String,
  javaClass: String,
  dataType: Int,
  remarks: Option[String],
)

object Udt {
  def getUdts(connection: Connection): List[Udt] = {
    val udtsRs = connection.getMetaData.getUDTs(null, null, null, null)
    try {
      val res = mutable.ListBuffer[Udt]()
      while (udtsRs.next()) {
        res.append(fromResultSet(udtsRs))
      }
      res.toList
    } finally {
      udtsRs.close()
    }
  }

  private def fromResultSet(resultSet: ResultSet): Udt =
    Udt(
      resultSet.getString("TYPE_SCHEM"),
      resultSet.getString("TYPE_NAME"),
      resultSet.getString("CLASS_NAME"),
      resultSet.getInt("DATA_TYPE"),
      Option(resultSet.getString("REMARKS")),
    )
}

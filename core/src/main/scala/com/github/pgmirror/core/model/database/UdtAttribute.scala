package com.github.pgmirror.core.model.database

import java.sql.{Connection, ResultSet}
import scala.collection.mutable

case class UdtAttribute(
  udtSchema: String,
  udtName: String,
  attributeName: String,
  ordinalPosition: Int,
  isNullable: Boolean,
  dataType: String,
)

object UdtAttribute {
  def fromResultSet(rs: ResultSet): UdtAttribute =
    UdtAttribute(
      udtSchema = rs.getString("TYPE_SCHEM"),
      udtName = rs.getString("TYPE_NAME"),
      attributeName = rs.getString("ATTR_NAME"),
      ordinalPosition = rs.getInt("ORDINAL_POSITION"),
      isNullable = rs.getString("IS_NULLABLE") == "YES",
      dataType = rs.getString("ATTR_TYPE_NAME"),
    )

  def getAttributesForUdt(connection: Connection, udt: Udt): List[UdtAttribute] = {
    val rs = connection.getMetaData.getAttributes(null, udt.udtSchema, udt.udtName, null)

    try {
      val res = mutable.ListBuffer[UdtAttribute]()
      while (rs.next()) {
        res.append(fromResultSet(rs))
      }
      res.toList
    } finally {
      rs.close()
    }
  }
}

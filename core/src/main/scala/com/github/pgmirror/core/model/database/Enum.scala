package com.github.pgmirror.core.model.database

import java.sql.ResultSet

case class Enum(
  enumSchema: String,
  enumName: String,
  enumValues: List[String],
)
object Enum {
  val sql: String =
    """select n.nspname as enum_schema,
      |       t.typname as enum_name,
      |       array_agg(e.enumlabel) as enum_values
      |from pg_type t
      |   join pg_enum e on t.oid = e.enumtypid
      |   join pg_catalog.pg_namespace n ON n.oid = t.typnamespace
      |group by n.nspname, t.typname""".stripMargin

  private def fromResultSet(rs: ResultSet): Enum = {
    val enumValues =
      rs.getArray("enum_values").getArray.asInstanceOf[Array[String]]

    Enum(
      rs.getString("enum_schema"),
      rs.getString("enum_name"),
      enumValues.toList,
    )
  }

}

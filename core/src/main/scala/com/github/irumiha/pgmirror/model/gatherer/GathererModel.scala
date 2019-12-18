package com.github.irumiha.pgmirror.model.gatherer

import java.sql.ResultSet

case class PgForeignKeys(
  tableSchema: String,
  tableName: String,
  constraintSchema: String,
  constraintName: String,
  columnName: String,
  foreignTableSchema: String,
  foreignTableName: String,
  foreignColumnName: String
)
object PgForeignKeys {
  def fromResultSet(rs: ResultSet): PgForeignKeys = {
    PgForeignKeys(
      tableSchema = rs.getString("table_schema"),
      tableName = rs.getString("table_name"),
      constraintSchema = rs.getString("constraint_schema"),
      constraintName = rs.getString("constraint_name"),
      columnName = rs.getString("column_name"),
      foreignTableSchema = rs.getString("foreign_table_schema"),
      foreignTableName = rs.getString("foreign_table_name"),
      foreignColumnName = rs.getString("foreign_column_name"),
    )
  }

  val sql: String =
    """SELECT
      |    tc.table_schema,
      |    tc.table_name,
      |    tc.constraint_schema,
      |    tc.constraint_name,
      |    kcu.column_name,
      |    ccu.table_schema AS foreign_table_schema,
      |    ccu.table_name AS foreign_table_name,
      |    ccu.column_name AS foreign_column_name
      |FROM
      |    information_schema.table_constraints AS tc
      |    JOIN information_schema.key_column_usage AS kcu
      |      ON tc.constraint_name = kcu.constraint_name
      |      AND tc.table_schema = kcu.table_schema
      |      AND tc.constraint_schema = tc.constraint_schema
      |    JOIN information_schema.constraint_column_usage AS ccu
      |      ON ccu.constraint_name = tc.constraint_name
      |      AND ccu.constraint_schema = tc.constraint_schema
      |      AND ccu.table_schema = tc.table_schema
      |WHERE tc.constraint_type = 'FOREIGN KEY'
      |""".stripMargin

}

case class PgTables(tableSchema: String, tableName: String, tableType: String, isInsertableInto: Boolean, description: Option[String])
object PgTables {
  def fromResultSet(rs: ResultSet): PgTables =
    PgTables(
      tableSchema = rs.getString("table_schema"),
      tableName = rs.getString("table_name"),
      tableType = rs.getString("table_type"),
      isInsertableInto = rs.getString("is_insertable_into") == "YES",
      description = Option(rs.getString("table_description")),
    )

  val sql: String =
    """select table_schema, table_name, table_type, is_insertable_into, obj_description(pg_class.oid, 'pg_class') as table_description
      |from information_schema.tables JOIN pg_catalog.pg_class ON relnamespace=table_schema::regnamespace::oid AND relname=table_name
      |""".stripMargin
}
case class PgColumns(
  tableSchema: String,
  tableName: String,
  columnName: String,
  ordinalPosition: Int,
  columnDefault: String,
  isNullable: Boolean,
  isPrimaryKey: Boolean,
  dataType: String,
  udtSchema: String,
  udtName: String,
  description: Option[String]
)
object PgColumns {
  def fromResultSet(rs: ResultSet): PgColumns =
    PgColumns(
      tableSchema = rs.getString("table_schema"),
      tableName = rs.getString("table_name"),
      columnName = rs.getString("column_name"),
      ordinalPosition = rs.getInt("ordinal_position"),
      columnDefault = rs.getString("column_default"),
      isNullable = rs.getString("is_nullable") == "YES",
      dataType = rs.getString("data_type"),
      udtSchema = rs.getString("udt_schema"),
      udtName = rs.getString("udt_name"),
      isPrimaryKey = rs.getBoolean("is_primary"),
      description = Option(rs.getString("column_description"))
    )

  val sql: String =
    """with is_primary as (
      | select table_schema, table_name, a.attname, a.attnum
      | from information_schema.tables
      |      join pg_index i on i.indrelid = ('"'||table_schema||'".'||'"'||table_name||'"')::regclass
      |      join pg_attribute a on a.attrelid=i.indrelid AND a.attnum = ANY(i.indkey)
      |      where table_type='BASE TABLE' and i.indisprimary
      |)
      |select table_schema, table_name, column_name, ordinal_position, column_default, is_nullable, data_type, udt_schema, udt_name,
      |       exists(select * from is_primary ip where ip.table_schema=table_schema and ip.table_name=table_name and ip.attnum=ordinal_position) as is_primary,
      |       col_description(pg_class.oid, ordinal_position) as column_description
      |from information_schema.columns JOIN pg_catalog.pg_class ON relnamespace=table_schema::regnamespace::oid AND relname=table_name
      |order by table_schema, table_name, ordinal_position
      |""".stripMargin

}

case class PgUdtAttributes(udtSchema: String, udtName: String, attributeName: String, ordinalPosition: Int, isNullable: Boolean, dataType: String, attributeUdtSchema: String, attributeUdtName: String)
object PgUdtAttributes {
  def fromResultSet(rs: ResultSet): PgUdtAttributes =
    PgUdtAttributes(
      udtSchema = rs.getString("udt_schema"),
      udtName = rs.getString("udt_name"),
      attributeName = rs.getString("attribute_name"),
      ordinalPosition = rs.getInt("ordinal_position"),
      isNullable = rs.getString("is_nullable") == "YES",
      dataType = rs.getString("data_type"),
      attributeUdtSchema = rs.getString("attribute_udt_schema"),
      attributeUdtName = rs.getString("attribute_udt_name")
    )

  val sql: String =
    """select udt_schema, udt_name, attribute_name, ordinal_position, is_nullable, data_type, attribute_udt_schema, attribute_udt_name
      |from information_schema.attributes
      |""".stripMargin

}

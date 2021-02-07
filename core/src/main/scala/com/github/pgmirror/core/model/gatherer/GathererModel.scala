package com.github.pgmirror.core.model.gatherer

import java.sql.{Connection, ResultSet}
import scala.collection.mutable

case class PgForeignKey(
  tableSchema: String,
  tableName: String,
  columnName: String,
  foreignTableSchema: String,
  foreignTableName: String,
  foreignColumnName: String,
  keySeq: Int,
  fkName: String,
  pkName: String
)
object PgForeignKey {
  def getForTable(schema: String, table: String)(conn: Connection): List[PgForeignKey] = {
    val importedKeysRs = conn.getMetaData.getImportedKeys(null, schema, table)
    try {
      val res = mutable.ListBuffer[PgForeignKey]()
      while (importedKeysRs.next()) {
        res.append(fromResultSet(importedKeysRs))
      }
      res.toList
    } finally {
      importedKeysRs.close()
    }
  }

  def fromResultSet(rs: ResultSet): PgForeignKey = {
    PgForeignKey(
      tableSchema = rs.getString("FKTABLE_SCHEM"),
      tableName = rs.getString("FKTABLE_NAME"),
      columnName = rs.getString("FKCOLUMN_NAME"),
      foreignTableSchema = rs.getString("PKTABLE_SCHEM"),
      foreignTableName = rs.getString("PKTABLE_NAME"),
      foreignColumnName = rs.getString("PKCOLUMN_NAME"),
      keySeq = rs.getInt("KEY_SEQ"),
      fkName = rs.getString("FK_NAME"),
      pkName = rs.getString("PK_NAME")
    )
  }
}

case class PgTable(
  tableSchema: String,
  tableName: String,
  tableType: String,
  description: Option[String],
)
object PgTable {
  def fromResultSet(rs: ResultSet): PgTable =
    PgTable(
      tableSchema = rs.getString("TABLE_SCHEM"),
      tableName = rs.getString("TABLE_NAME"),
      tableType = rs.getString("TABLE_TYPE"),
      description = Option(rs.getString("REMARKS")),
    )

  def getTables(connection: Connection): List[PgTable] = {
    val tableTypes = getTableTypes(connection).intersect(List("VIEW", "TABLE")).toArray
    val tablesRs = connection.getMetaData.getTables(null, null, null, tableTypes)
    try {
      val res = mutable.ListBuffer[PgTable]()
      while (tablesRs.next()) {
        res.append(fromResultSet(tablesRs))
      }
      res.toList
    } finally {
      tablesRs.close()
    }
  }

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

case class PgColumn(
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
object PgColumn {
  def fromResultSet(rs: ResultSet): PgColumn =
    PgColumn(
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

  def getColumns(connection: Connection, tables: List[PgTable]): Map[PgTable, List[PgColumn]] = {
    val allColumns = mutable.HashMap[PgTable, List[PgColumn]]()
    tables.foreach { t =>
      val columnsList = mutable.ListBuffer[PgColumn]()
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

case class PgUdt(
  udtSchema: String,
  udtName: String,
  javaClass: String,
  dataType: Int,
  remarks: Option[String]
)

object PgUdt {
  def fromResultSet(resultSet: ResultSet): PgUdt =
    PgUdt(
      resultSet.getString("TYPE_SCHEM"),
      resultSet.getString("TYPE_NAME"),
      resultSet.getString("CLASS_NAME"),
      resultSet.getInt("DATA_TYPE"),
      Option(resultSet.getString("REMARKS"))
    )

  def getUdts(connection: Connection): List[PgUdt] = {
    val udtsRs = connection.getMetaData.getUDTs(null, null, null, null)
    try {
      val res = mutable.ListBuffer[PgUdt]()
      while (udtsRs.next()) {
        res.append(fromResultSet(udtsRs))
      }
      res.toList
    } finally {
      udtsRs.close()
    }
  }
}

case class PgUdtAttribute(
  udtSchema: String,
  udtName: String,
  attributeName: String,
  ordinalPosition: Int,
  isNullable: Boolean,
  dataType: String,
)

object PgUdtAttribute {
  def fromResultSet(rs: ResultSet): PgUdtAttribute =
    PgUdtAttribute(
      udtSchema = rs.getString("TYPE_SCHEM"),
      udtName = rs.getString("TYPE_NAME"),
      attributeName = rs.getString("ATTR_NAME"),
      ordinalPosition = rs.getInt("ORDINAL_POSITION"),
      isNullable = rs.getString("IS_NULLABLE") == "YES",
      dataType = rs.getString("ATTR_TYPE_NAME")
    )

  def getAttributesForUdt(connection: Connection, udt: PgUdt): List[PgUdtAttribute] = {
    val rs = connection.getMetaData.getAttributes(null, udt.udtSchema,udt.udtName, null)

    try {
      val res = mutable.ListBuffer[PgUdtAttribute]()
      while (rs.next()) {
        res.append(fromResultSet(rs))
      }
      res.toList
    } finally {
      rs.close()
    }
  }
}

case class PgEnum(
  enumSchema: String,
  enumName: String,
  enumValues: List[String],
)
object PgEnum {
  val sql: String =
    """select n.nspname as enum_schema,
      |       t.typname as enum_name,
      |       array_agg(e.enumlabel) as enum_values
      |from pg_type t
      |   join pg_enum e on t.oid = e.enumtypid
      |   join pg_catalog.pg_namespace n ON n.oid = t.typnamespace
      |group by n.nspname, t.typname""".stripMargin

  def fromResultSet(rs: ResultSet): PgEnum = {
    val enumValues =
      rs.getArray("enum_values").getArray.asInstanceOf[Array[String]]

    PgEnum(
      rs.getString("enum_schema"),
      rs.getString("enum_name"),
      enumValues.toList,
    )
  }

}

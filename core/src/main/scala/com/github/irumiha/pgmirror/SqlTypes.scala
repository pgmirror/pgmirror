package com.github.irumiha.pgmirror

import java.sql.Types._

object SqlTypes {

  def typeMapping(sqlType: Int): String = sqlType match {
    case BIT => "Boolean"
    case TINYINT => "Int"
    case SMALLINT => "Int"
    case INTEGER => "Int"
    case BIGINT => "BigInt"
    case FLOAT => "Float"
    case REAL => "BigDecimal"
    case DOUBLE => "Double"
    case NUMERIC => "BigDecimal"
    case DECIMAL => "BigDecimal"
    case CHAR => "String"
    case VARCHAR => "String"
    case LONGVARCHAR => "String"
    case DATE => "java.sql.Date"
    case TIME => "java.sql.Time"
    case TIMESTAMP => "java.sql.Timestamp"
    case BINARY => "Array[Byte]"
    case VARBINARY => "Array[Byte]"
    case LONGVARBINARY => "Array[Byte]"
  }

  def typeName(sqlType: Int): String = sqlType match {
    case BIT => "BIT"
    case TINYINT => "TINYINT"
    case SMALLINT => "SMALLINT"
    case INTEGER => "INTEGER"
    case BIGINT => "BIGINT"
    case FLOAT => "FLOAT"
    case REAL => "REAL"
    case DOUBLE => "DOUBLE"
    case NUMERIC => "NUMERIC"
    case DECIMAL => "DECIMAL"
    case CHAR => "CHAR"
    case VARCHAR => "VARCHAR"
    case LONGVARCHAR => "LONGVARCHAR"
    case DATE => "DATE"
    case TIME => "TIME"
    case TIMESTAMP => "TIMESTAMP"
    case BINARY => "BINARY"
    case VARBINARY => "VARBINARY"
    case LONGVARBINARY => "LONGVARBINARY"
  }

}

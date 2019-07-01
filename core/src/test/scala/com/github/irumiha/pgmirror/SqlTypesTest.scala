package com.github.irumiha.pgmirror

import org.scalatest.FunSuite

class SqlTypesTest extends FunSuite {

  test("testTypeMapping") {
    val cases = Seq(
      ("pg_catalog", "text", "text"),
      ("pg_catalog", "int4", "integer"),
      ("pg_catalog", "uuid", "uuid"),
      ("pg_catalog", "date", "date"),
      ("public", "basic_info", "USER-DEFINED"),
      ("public", "customer", "USER-DEFINED"),
      ("public", "_invoice_item", "ARRAY"),
      ("pg_catalog", "timetz", "time with time zone"),
      ("pg_catalog", "date", "date"),
      ("pg_catalog", "numeric", "numeric"),
      ("pg_catalog", "bool", "boolean"),
      ("pg_catalog", "_text", "ARRAY"),
      ("pg_catalog", "bool", "boolean"),
    )

    cases.foreach { case (typSchema, typName, datType) =>
      println(SqlTypes.typeMapping(typSchema, typName, datType))
    }
  }

}

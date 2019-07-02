package com.github.irumiha.pgmirror

import org.scalatest.{FunSuite, MustMatchers}

class SqlTypesTest extends FunSuite with MustMatchers {

  test("testTypeMapping") {
    val cases = Seq(
      ("pg_catalog", "text", "text", "String"),
      ("pg_catalog", "int4", "integer", "Int"),
      ("pg_catalog", "uuid", "uuid", "java.util.UUID"),
      ("pg_catalog", "date", "date", "java.sql.Date"),
      ("public", "basic_info", "USER-DEFINED", "public.BasicInfo"),
      ("", "basic_info", "USER-DEFINED", "BasicInfo"),
      ("public", "customer", "USER-DEFINED", "public.Customer"),
      ("public", "_invoice_item", "ARRAY", "Seq[public.InvoiceItem]"),
      ("", "_invoice_item", "ARRAY", "Seq[InvoiceItem]"),
      ("pg_catalog", "timetz", "time with time zone", "java.sql.Time"),
      ("pg_catalog", "date", "date", "java.sql.Date"),
      ("pg_catalog", "numeric", "numeric", "BigDecimal"),
      ("pg_catalog", "bool", "boolean", "Boolean"),
      ("pg_catalog", "_text", "ARRAY", "Seq[String]"),
    )

    cases.foreach { case (typSchema, typName, datType, result) =>
      SqlTypes.typeMapping(typSchema, typName, datType) must equal(Right(result))
    }
  }

}

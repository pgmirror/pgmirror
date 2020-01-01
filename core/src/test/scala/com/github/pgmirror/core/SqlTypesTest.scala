package com.github.pgmirror.core

import com.github.pgmirror.core.SqlTypes.ResolvedType
import org.scalatest.matchers.must.Matchers
import org.scalatest.funsuite.AnyFunSuite

class SqlTypesTest extends AnyFunSuite with Matchers {

  test("testTypeMapping") {
    val cases = Seq(
      ("pg_catalog", "text", "text", ResolvedType("String")),
      ("pg_catalog", "int4", "integer", ResolvedType("Int")),
      ("pg_catalog", "uuid", "uuid", ResolvedType("java.util.UUID")),
      ("pg_catalog", "date", "date", ResolvedType("java.time.LocalDate")),
      ("public", "basic_info", "USER-DEFINED", ResolvedType("package.prefix.public.models.BasicInfo", udt = true)),
      ("", "basic_info", "USER-DEFINED", ResolvedType("package.prefix.models.BasicInfo", udt = true)),
      ("public", "customer", "USER-DEFINED", ResolvedType("package.prefix.public.models.Customer", udt = true)),
      ("public", "_invoice_item", "ARRAY", ResolvedType("Seq[package.prefix.public.models.InvoiceItem]", udt = true)),
      ("", "_invoice_item", "ARRAY", ResolvedType("Seq[package.prefix.models.InvoiceItem]", udt = true)),
      ("pg_catalog", "timetz", "time with time zone", ResolvedType("java.time.LocalTime")),
      ("pg_catalog", "numeric", "numeric", ResolvedType("BigDecimal")),
      ("pg_catalog", "bool", "boolean", ResolvedType("Boolean")),
      ("pg_catalog", "_text", "ARRAY", ResolvedType("Seq[String]")),
    )

    cases.foreach { case (typSchema, typName, datType, result) =>
      SqlTypes.typeMapping("package.prefix", typSchema, typName, datType) must equal(Right(result))
    }
  }

}

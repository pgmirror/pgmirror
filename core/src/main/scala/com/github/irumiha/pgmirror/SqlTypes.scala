package com.github.irumiha.pgmirror

object SqlTypes {

  def typeMapping(pgSchema: String, pgType: String, pgDataType: String): Either[Throwable, String] = (pgSchema, pgType, pgDataType) match {
    case (_, _, "bigint") => Right("Long")
    case (_, _, "int8") => Right("Long")
    case (_, _, "bigserial") => Right("Long")
    case (_, _, "boolean") => Right("Boolean")
    case (_, _, "bool") => Right("Boolean")
    case (_, _, "bytea") => Right("Array[Byte]")
    case (_, _, "character") => Right("String")
    case (_, _, "character varying") => Right("String")
    case (_, _, "varchar") => Right("String")
    case (_, _, "date") => Right("java.sql.Date")
    case (_, _, "double precision") => Right("Double")
    case (_, _, "float8") => Right("Double")
    case (_, _, "integer") => Right("Int")
    case (_, _, "int") => Right("Int")
    case (_, _, "int4") => Right("Int")
    case (_, _, "money") => Right("BigDecimal")
    case (_, _, "numeric") => Right("BigDecimal")
    case (_, _, "real") => Right("Float")
    case (_, _, "float4") => Right("Float")
    case (_, _, "smallint") => Right("Int")
    case (_, _, "int2") => Right("Int")
    case (_, _, "smallserial") => Right("Int")
    case (_, _, "serial") => Right("Int")
    case (_, _, "text") => Right("String")
    case (_, _, "time") => Right("java.sql.Time")
    case (_, _, "timetz") => Right("java.sql.Time")
    case (_, _, "time with time zone") => Right("java.sql.Time")
    case (_, _, "timestamp") => Right("java.time.Instant")
    case (_, _, "timestamptz") => Right("java.time.Instant")
    case (_, _, "timestamp with time zone") => Right("java.time.Instant")
    case (_, _, "uuid") => Right("java.util.UUID")
    case ("pg_catalog", pgt, "ARRAY") => typeMapping("", "", pgt.replaceFirst("_", "")).map(t => s"Seq[$t]")
    case (pgs, pgt, "ARRAY") =>
      val underlying =
        pgt.replaceFirst("_", "")
          .split("_")
          .filterNot(_.isEmpty)
          .map(_.capitalize)
          .mkString

      val underlyingPackage = pgs
      val finalType = if (underlyingPackage.isEmpty) underlying else s"$underlyingPackage.$underlying"
      Right(s"Seq[$finalType]")
    case (pgs, pgt, "USER-DEFINED") =>
      val underlying =
        pgt.split("_")
          .filterNot(_.isEmpty)
          .map(_.capitalize)
          .mkString

      val underlyingPackage = pgs
      val finalType = if (underlyingPackage.isEmpty) underlying else s"$underlyingPackage.$underlying"
      Right(s"$finalType")
    case (_, _, _) => Left(new Exception(s"Mapping for $pgSchema, $pgType, $pgDataType not found!"))
  }
}

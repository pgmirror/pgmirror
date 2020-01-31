package com.github.pgmirror.core

object SqlTypes {

  case class ResolvedType(modelType: String, udt: Boolean = false)

  def typeMapping(
    packagePrefix: String,
    pgSchema: String,
    pgType: String,
    pgDataType: String
  ): Either[Throwable, ResolvedType] = (pgSchema, pgType, pgDataType) match {
    case (_, _, "bigint") => Right(ResolvedType("Long"))
    case (_, _, "int8") => Right(ResolvedType("Long"))
    case (_, _, "bigserial") => Right(ResolvedType("Long"))
    case (_, _, "boolean") => Right(ResolvedType("Boolean"))
    case (_, _, "bool") => Right(ResolvedType("Boolean"))
    case (_, _, "bytea") => Right(ResolvedType("Array[Byte]"))
    case (_, _, "character") => Right(ResolvedType("String"))
    case (_, _, "character varying") => Right(ResolvedType("String"))
    case (_, _, "varchar") => Right(ResolvedType("String"))
    case (_, _, "date") => Right(ResolvedType("java.time.LocalDate"))
    case (_, _, "double precision") => Right(ResolvedType("Double"))
    case (_, _, "float8") => Right(ResolvedType("Double"))
    case (_, _, "integer") => Right(ResolvedType("Int"))
    case (_, _, "int") => Right(ResolvedType("Int"))
    case (_, _, "int4") => Right(ResolvedType("Int"))
    case (_, _, "money") => Right(ResolvedType("BigDecimal"))
    case (_, _, "numeric") => Right(ResolvedType("BigDecimal"))
    case (_, _, "real") => Right(ResolvedType("Float"))
    case (_, _, "float4") => Right(ResolvedType("Float"))
    case (_, _, "smallint") => Right(ResolvedType("Int"))
    case (_, _, "int2") => Right(ResolvedType("Int"))
    case (_, _, "smallserial") => Right(ResolvedType("Int"))
    case (_, _, "serial") => Right(ResolvedType("Int"))
    case (_, _, "text") => Right(ResolvedType("String"))
    case (_, _, "time") => Right(ResolvedType("java.time.LocalTime"))
    case (_, _, "timetz") => Right(ResolvedType("java.time.LocalTime"))
    case (_, _, "time with time zone") => Right(ResolvedType("java.time.LocalTime"))
    case (_, _, "timestamp") => Right(ResolvedType("java.time.Instant"))
    case (_, _, "timestamptz") => Right(ResolvedType("java.time.Instant"))
    case (_, _, "timestamp with time zone") => Right(ResolvedType("java.time.Instant"))
    case (_, _, "timestamp without time zone") => Right(ResolvedType("java.time.Instant"))
    case (_, _, "uuid") => Right(ResolvedType("java.util.UUID"))
    case (_, _, "json") => Right(ResolvedType("io.circe.Json"))
    case (_, _, "jsonb") => Right(ResolvedType("io.circe.Json"))
    case ("pg_catalog", pgt, "ARRAY") =>
      typeMapping(
        packagePrefix,
        "",
        "", pgt.replaceFirst("_", "")
      ).map(t => ResolvedType(s"Array[${t.modelType}]", t.udt))
    case (pgs, pgt, "ARRAY") =>
      val underlying =
        pgt.replaceFirst("_", "")
          .split("_")
          .filterNot(_.isEmpty)
          .map(_.capitalize)
          .mkString

      val underlyingPackage = pgs
      val finalType = if (underlyingPackage.isEmpty) {
        s"$packagePrefix.${underlying}"
      } else {
        s"$packagePrefix.$underlyingPackage.$underlying"
      }
      Right(ResolvedType(s"Array[$finalType]", udt = true))
    case (pgs, pgt, "USER-DEFINED") =>
      val underlying =
        pgt.split("_")
          .filterNot(_.isEmpty)
          .map(_.capitalize)
          .mkString

      val underlyingPackage = pgs
      val finalType = if (underlyingPackage.isEmpty) {
        s"$packagePrefix.${underlying}"
      } else {
        s"$packagePrefix.$underlyingPackage.$underlying"
      }
      Right(ResolvedType(s"$finalType", udt = true))
    case (_, _, _) => Left(new Exception(s"Mapping for ($pgSchema, $pgType, $pgDataType) not found!"))
  }
}

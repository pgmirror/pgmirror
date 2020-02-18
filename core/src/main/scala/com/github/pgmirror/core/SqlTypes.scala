package com.github.pgmirror.core

object SqlTypes {

  case class ResolvedType(modelType: String, udt: Boolean = false)

  private val longType = ResolvedType("Long")
  private val intType  = ResolvedType("Int")

  private val doubleType  = ResolvedType("Double")
  private val decimalType = ResolvedType("BigDecimal")
  private val floatType   = ResolvedType("Float")

  private val boolType = ResolvedType("Boolean")

  private val stringType = ResolvedType("String")

  private val localDateType = ResolvedType("java.time.LocalDate")
  private val localTimeType = ResolvedType("java.time.LocalTime")
  private val instantType   = ResolvedType("java.time.Instant")

  private val byteArrayType = ResolvedType("Array[Byte]")

  private val uuidType = ResolvedType("java.util.UUID")

  private val jsonType = ResolvedType("io.circe.Json")

  def typeMapping(
      packagePrefix: String,
      pgSchema: String,
      pgType: String,
      pgDataType: String,
  ): Either[Throwable, ResolvedType] = (pgSchema, pgType, pgDataType) match {
    case (_, _, "bigint")                      => Right(longType)
    case (_, _, "int8")                        => Right(longType)
    case (_, _, "bigserial")                   => Right(longType)
    case (_, _, "boolean")                     => Right(boolType)
    case (_, _, "bool")                        => Right(boolType)
    case (_, _, "integer")                     => Right(intType)
    case (_, _, "int")                         => Right(intType)
    case (_, _, "int4")                        => Right(intType)
    case (_, _, "smallint")                    => Right(intType)
    case (_, _, "int2")                        => Right(intType)
    case (_, _, "smallserial")                 => Right(intType)
    case (_, _, "serial")                      => Right(intType)
    case (_, _, "double precision")            => Right(doubleType)
    case (_, _, "float8")                      => Right(doubleType)
    case (_, _, "real")                        => Right(floatType)
    case (_, _, "float4")                      => Right(floatType)
    case (_, _, "money")                       => Right(decimalType)
    case (_, _, "numeric")                     => Right(decimalType)
    case (_, _, "bytea")                       => Right(byteArrayType)
    case (_, _, "character")                   => Right(stringType)
    case (_, _, "character varying")           => Right(stringType)
    case (_, _, "varchar")                     => Right(stringType)
    case (_, _, "text")                        => Right(stringType)
    case (_, _, "date")                        => Right(localDateType)
    case (_, _, "time")                        => Right(localTimeType)
    case (_, _, "timetz")                      => Right(localTimeType)
    case (_, _, "time with time zone")         => Right(localTimeType)
    case (_, _, "timestamp")                   => Right(instantType)
    case (_, _, "timestamptz")                 => Right(instantType)
    case (_, _, "timestamp with time zone")    => Right(instantType)
    case (_, _, "timestamp without time zone") => Right(instantType)
    case (_, _, "uuid")                        => Right(uuidType)
    case (_, _, "json")                        => Right(jsonType)
    case (_, _, "jsonb")                       => Right(jsonType)
    case ("pg_catalog", pgt, "ARRAY") =>
      typeMapping(
        packagePrefix,
        "",
        "",
        pgt.replaceFirst("_", ""),
      ).map(t => ResolvedType(s"Array[${t.modelType}]", t.udt))

    case (pgs, pgt, "ARRAY") =>
      val underlying: String = camelCaseize(pgt.replaceFirst("_", ""))
      val modelType          = s"Array[${finalType(packagePrefix, pgs, underlying)}]"

      Right(ResolvedType(modelType, udt = true))

    case (pgs, pgt, "USER-DEFINED") =>
      val underlying: String = camelCaseize(pgt)
      val modelType: String  = finalType(packagePrefix, pgs, underlying)

      Right(ResolvedType(modelType, udt = true))

    case (_, _, _) =>
      Left(
        new Exception(
          s"Mapping for ($pgSchema, $pgType, $pgDataType) not found!",
        ),
      )
  }

  private def camelCaseize(pgt: String): String =
    pgt
      .split("_")
      .filterNot(_.isEmpty)
      .map(_.capitalize)
      .mkString

  private def finalType(
      packagePrefix: String,
      pgs: String,
      underlying: String,
  ): String = {
    val underlyingPackage = pgs
    val finalType = if (underlyingPackage.isEmpty) {
      s"$packagePrefix.${underlying}"
    } else {
      s"$packagePrefix.$underlyingPackage.$underlying"
    }
    finalType
  }
}

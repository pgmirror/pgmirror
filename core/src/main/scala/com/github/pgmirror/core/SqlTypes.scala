package com.github.pgmirror.core

import scala.util.Try

object SqlTypes {

  case class ResolvedType(modelType: String, udt: Boolean = false)

  private val longType = ResolvedType("Long")
  private val intType = ResolvedType("Int")

  private val doubleType = ResolvedType("Double")
  private val decimalType = ResolvedType("BigDecimal")
  private val floatType = ResolvedType("Float")

  private val boolType = ResolvedType("Boolean")

  private val stringType = ResolvedType("String")

  private val localDateType = ResolvedType("java.time.LocalDate")
  private val localTimeType = ResolvedType("java.time.LocalTime")
  private val instantType = ResolvedType("java.time.Instant")

  private val byteArrayType = ResolvedType("Array[Byte]")

  private val uuidType = ResolvedType("java.util.UUID")

  private val jsonType = ResolvedType("io.circe.Json")

  def typeMapping(
    packagePrefix: String,
    pgSchema: String,
    pgType: String,
    pgDataType: String,
  ): Either[Throwable, ResolvedType] =
    Try {
      (pgSchema, pgType, pgDataType) match {
        case (_, _, "bigint")                      => longType
        case (_, _, "int8")                        => longType
        case (_, _, "bigserial")                   => longType
        case (_, _, "boolean")                     => boolType
        case (_, _, "bool")                        => boolType
        case (_, _, "integer")                     => intType
        case (_, _, "int")                         => intType
        case (_, _, "int4")                        => intType
        case (_, _, "smallint")                    => intType
        case (_, _, "int2")                        => intType
        case (_, _, "smallserial")                 => intType
        case (_, _, "serial")                      => intType
        case (_, _, "double precision")            => doubleType
        case (_, _, "float8")                      => doubleType
        case (_, _, "real")                        => floatType
        case (_, _, "float4")                      => floatType
        case (_, _, "money")                       => decimalType
        case (_, _, "numeric")                     => decimalType
        case (_, _, "bytea")                       => byteArrayType
        case (_, _, "character")                   => stringType
        case (_, _, "character varying")           => stringType
        case (_, _, "varchar")                     => stringType
        case (_, _, "text")                        => stringType
        case (_, _, "date")                        => localDateType
        case (_, _, "time")                        => localTimeType
        case (_, _, "timetz")                      => localTimeType
        case (_, _, "time with time zone")         => localTimeType
        case (_, _, "timestamp")                   => instantType
        case (_, _, "timestamptz")                 => instantType
        case (_, _, "timestamp with time zone")    => instantType
        case (_, _, "timestamp without time zone") => instantType
        case (_, _, "uuid")                        => uuidType
        case (_, _, "json")                        => jsonType
        case (_, _, "jsonb")                       => jsonType
        case ("pg_catalog", pgt, "ARRAY") =>
          typeMapping(
            packagePrefix,
            "",
            "",
            pgt.replaceFirst("_", ""),
          ) match {
            case Left(err)  => throw err
            case Right(tpe) => ResolvedType(s"Array[${tpe.modelType}]", tpe.udt)
          }

        case (pgs, pgt, "ARRAY") =>
          val underlying: String = Names.toClassCamelCase(pgt.replaceFirst("_", ""))
          val modelType = s"Array[${finalType(packagePrefix, pgs, underlying)}]"

          ResolvedType(modelType, udt = true)

        case (pgs, pgt, "USER-DEFINED") =>
          val underlying: String = Names.toClassCamelCase(pgt)
          val modelType: String = finalType(packagePrefix, pgs, underlying)

          ResolvedType(modelType, udt = true)

        case (_, _, _) =>
          throw new Exception(
            s"Mapping for ($pgSchema, $pgType, $pgDataType) not found!",
          )

      }
    }.toEither

  private def finalType(
    packagePrefix: String,
    pgs: String,
    underlying: String,
  ): String = {
    val underlyingPackage = pgs
    val finalType = if (underlyingPackage.isEmpty) {
      s"$packagePrefix.$underlying"
    } else {
      s"$packagePrefix.$underlyingPackage.$underlying"
    }
    finalType
  }
}

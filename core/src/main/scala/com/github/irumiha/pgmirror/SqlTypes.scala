package com.github.irumiha.pgmirror

import com.google.common.base.CaseFormat

object SqlTypes {

  def typeMapping(pgSchema: String, pgType: String, pgDataType: String): Either[Throwable, String] = (pgSchema, pgType, pgDataType) match {
      case (           _,   _, "bigint")                   => Right("Long")
      case (           _,   _, "int8")                     => Right("Long")
      case (           _,   _, "bigserial")                => Right("Long")
      case (           _,   _, "boolean")                  => Right("Boolean")
      case (           _,   _, "bytea")                    => Right("Array[Byte]")
      case (           _,   _, "character")                => Right("String")
      case (           _,   _, "character varying")        => Right("String")
      case (           _,   _, "varchar")                  => Right("String")
      case (           _,   _, "date")                     => Right("java.sql.Date")
      case (           _,   _, "double precision")         => Right("Double")
      case (           _,   _, "float8")                   => Right("Double")
      case (           _,   _, "integer")                  => Right("Int")
      case (           _,   _, "int")                      => Right("Int")
      case (           _,   _, "int4")                     => Right("Int")
      case (           _,   _, "money")                    => Right("BigDecimal")
      case (           _,   _, "numeric")                  => Right("BigDecimal")
      case (           _,   _, "real")                     => Right("Float")
      case (           _,   _, "float4")                   => Right("Float")
      case (           _,   _, "smallint")                 => Right("Int")
      case (           _,   _, "int2")                     => Right("Int")
      case (           _,   _, "smallserial")              => Right("Int")
      case (           _,   _, "serial")                   => Right("Int")
      case (           _,   _, "text")                     => Right("Int")
      case (           _,   _, "time")                     => Right("java.sql.Time")
      case (           _,   _, "timetz")                   => Right("java.sql.Time")
      case (           _,   _, "timestamp")                => Right("java.time.Instant")
      case (           _,   _, "timestamptz")              => Right("java.time.Instant")
      case (           _,   _, "timestamp with time zone") => Right("java.time.Instant")
      case (           _,   _, "uuid")                     => Right("java.util.UUID")
      case ("pg_catalog", pgt, "ARRAY")                    => typeMapping("", "", pgt.replaceFirst("_", "")).map(t => s"Seq[$t]")
      case (         pgs, pgt, "ARRAY")                    =>
        val underlying =
          {
            val v = pgt.replaceFirst("_", "")

            if (v.contains("_"))
              CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, v)
            else
              v
          }

        val underlyingPackage = pgs
        val finalType = if (underlyingPackage.isEmpty) underlying else s"$underlyingPackage.$underlying"
        Right(s"Seq[$finalType]")
      case (         pgs, pgt, "USER-DEFINED")             =>
        val underlying =
        {
          val v = pgt.replaceFirst("_", "")

          if (v.contains("_"))
            CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, v)
          else
            v
        }
        val underlyingPackage = pgs
        val finalType = if (underlyingPackage.isEmpty) underlying else s"$underlyingPackage.$underlying"
        Right(s"$finalType")
      case (_, _, _) => Left(new Exception(s"Mapping for $pgSchema, $pgType, $pgDataType not found!"))
  }
}

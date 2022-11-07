package com.github.pgmirror.core.model.generator

import scala.util.{Failure, Success, Try}

object SqlTypes {

  /* POSTGRES
  Column(public,alltypes,c1,1,,true,false,-5,int8,None)                                                Types.BIGINT
  Column(public,alltypes,c2,2,,true,false,-5,int8,None)                                                Types.BIGINT
  Column(public,alltypes,c3,3,nextval('alltypes_c3_seq'::regclass),false,false,-5,bigserial,None)      Types.BIGINT
  Column(public,alltypes,c4,4,,true,false,-7,bool,None)                                                Types.BIT
  Column(public,alltypes,c5,5,,true,false,-7,bool,None)                                                Types.BIT
  Column(public,alltypes,c6,6,,true,false,4,int4,None)                                                 Types.INTEGER
  Column(public,alltypes,c7,7,,true,false,4,int4,None)                                                 Types.INTEGER
  Column(public,alltypes,c8,8,,true,false,4,int4,None)                                                 Types.INTEGER
  Column(public,alltypes,c9,9,,true,false,5,int2,None)                                                 Types.SMALLINT
  Column(public,alltypes,c10,10,,true,false,5,int2,None)                                               Types.SMALLINT
  Column(public,alltypes,c11,11,nextval('alltypes_c11_seq'::regclass),false,false,5,int2,None)         Types.SMALLINT
  Column(public,alltypes,c12,12,nextval('alltypes_c12_seq'::regclass),false,false,4,serial,None)       Types.INTEGER
  Column(public,alltypes,c13,13,,true,false,8,float8,None)                                             Types.DOUBLE
  Column(public,alltypes,c14,14,,true,false,8,float8,None)                                             Types.DOUBLE
  Column(public,alltypes,c15,15,,true,false,7,float4,None)                                             Types.REAL
  Column(public,alltypes,c16,16,,true,false,7,float4,None)                                             Types.REAL
  Column(public,alltypes,c17,17,,true,false,8,money,None)                                              Types.DOUBLE
  Column(public,alltypes,c18,18,,true,false,2,numeric,None)                                            Types.NUMERIC
  Column(public,alltypes,c19,19,,true,false,-2,bytea,None)                                             Types.BINARY
  Column(public,alltypes,c20,20,,true,false,1,bpchar,None)                                             Types.CHAR
  Column(public,alltypes,c21,21,,true,false,12,varchar,None)                                           Types.VARCHAR
  Column(public,alltypes,c22,22,,true,false,12,varchar,None)                                           Types.VARCHAR
  Column(public,alltypes,c23,23,,true,false,12,text,None)                                              Types.VARCHAR
  Column(public,alltypes,c24,24,,true,false,91,date,None)                                              Types.DATE
  Column(public,alltypes,c25,25,,true,false,92,timetz,None)                                            Types.TIME
  Column(public,alltypes,c26,26,,true,false,92,timetz,None)                                            Types.TIME
  Column(public,alltypes,c27,27,,true,false,93,timestamp,None)                                         Types.TIMESTAMP
  Column(public,alltypes,c28,28,,true,false,93,timestamptz,None)                                       Types.TIMESTAMP
  Column(public,alltypes,c29,29,,true,false,93,timestamptz,None)                                       Types.TIMESTAMP
  Column(public,alltypes,c30,30,,true,false,93,timestamp,None)                                         Types.TIMESTAMP
  Column(public,alltypes,c31,31,,true,false,1111,uuid,None)                                            Types.OTHER
  Column(public,alltypes,c32,32,,true,false,1111,json,None)                                            Types.OTHER
  Column(public,alltypes,c33,33,,true,false,1111,jsonb,None)                                           Types.OTHER
  Column(public,alltypes,c34,34,,true,false,2003,_text,None)                                           Types.ARRAY
  Column(public,alltypes,some_custom_type,35,,true,false,2002,"opendata"."my_custom_type",None)        Types.STRUCT
  Column(public,alltypes,some_custom_type_array,36,,true,false,1111,"opendata"."_my_custom_type",None) Types.OTHER
   */

  /* H2
  Column(public,alltypes,c1,1,,true,false,-5,bigint,Some())                                                                                   Types.BIGINT
  Column(public,alltypes,c2,2,,true,false,-5,bigint,Some())                                                                                   Types.BIGINT
  Column(public,alltypes,c3,3,NEXT VALUE FOR "public"."SYSTEM_SEQUENCE_6417D074_DB7D_409A_81A9_8A245B3B035D",false,false,-5,bigint,Some())    Types.BIGINT
  Column(public,alltypes,c4,4,,true,false,16,boolean,Some())                                                                                  Types.BOOLEAN
  Column(public,alltypes,c5,5,,true,false,16,boolean,Some())                                                                                  Types.BOOLEAN
  Column(public,alltypes,c6,6,,true,false,4,integer,Some())                                                                                   Types.INTEGER
  Column(public,alltypes,c7,7,,true,false,4,integer,Some())                                                                                   Types.INTEGER
  Column(public,alltypes,c8,8,,true,false,4,integer,Some())                                                                                   Types.INTEGER
  Column(public,alltypes,c9,9,,true,false,5,smallint,Some())                                                                                  Types.SMALLINT
  Column(public,alltypes,c10,10,,true,false,5,smallint,Some())                                                                                Types.SMALLINT
  Column(public,alltypes,c12,11,NEXT VALUE FOR "public"."SYSTEM_SEQUENCE_C6162D57_1E6E_4D48_A41A_435FF800B19C",false,false,4,integer,Some())  Types.INTEGER
  Column(public,alltypes,c13,12,,true,false,8,double,Some())                                                                                  Types.DOUBLE
  Column(public,alltypes,c14,13,,true,false,8,double,Some())                                                                                  Types.DOUBLE
  Column(public,alltypes,c15,14,,true,false,7,real,Some())                                                                                    Types.REAL
  Column(public,alltypes,c16,15,,true,false,7,real,Some())                                                                                    Types.REAL
  Column(public,alltypes,c17,16,,true,false,3,decimal,Some())                                                                                 Types.DECIMAL
  Column(public,alltypes,c18,17,,true,false,3,decimal,Some())                                                                                 Types.DECIMAL
  Column(public,alltypes,c19,18,,true,false,-3,varbinary,Some())                                                                              Types.VARBINARY
  Column(public,alltypes,c20,19,,true,false,1,char,Some())                                                                                    Types.CHAR
  Column(public,alltypes,c21,20,,true,false,12,varchar,Some())                                                                                Types.VARCHAR
  Column(public,alltypes,c22,21,,true,false,12,varchar,Some())                                                                                Types.VARCHAR
  Column(public,alltypes,c23,22,,true,false,2005,clob,Some())                                                                                 Types.CLOB
  Column(public,alltypes,c24,23,,true,false,91,date,Some())                                                                                   Types.DATE
  Column(public,alltypes,c26,24,,true,false,2013,time with time zone,Some())                                                                  Types.TIME_WITH_TIMEZONE
  Column(public,alltypes,c27,25,,true,false,93,timestamp,Some())                                                                              Types.TIMESTAMP
  Column(public,alltypes,c29,26,,true,false,2014,timestamp with time zone,Some())                                                             Types.TIMESTAMP_WITH_TIMEZONE
  Column(public,alltypes,c30,27,,true,false,93,timestamp,Some())                                                                              Types.TIMESTAMP
  Column(public,alltypes,c31,28,,true,false,-2,uuid,Some())                                                                                   Types.BINARY
  Column(public,alltypes,c32,29,,true,false,1111,json,Some())                                                                                 Types.OTHER
  Column(public,alltypes,c34,30,,true,false,2003,array,Some())                                                                                Types.ARRAY
   */

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

  private val dateRangeType = ResolvedType("(java.time.LocalDate, java.time.LocalDate)")

  private val inetType = ResolvedType("java.net.InetAddress")

  def typeMapping(
    packagePrefix: String,
    dbDataType: String,
  ): Try[ResolvedType] = {
    dbDataType match {
      case "bigint"                      => Success(longType)
      case "int8"                        => Success(longType)
      case "bigserial"                   => Success(longType)
      case "boolean"                     => Success(boolType)
      case "bool"                        => Success(boolType)
      case "integer"                     => Success(intType)
      case "int"                         => Success(intType)
      case "int4"                        => Success(intType)
      case "smallint"                    => Success(intType)
      case "int2"                        => Success(intType)
      case "smallserial"                 => Success(intType)
      case "serial"                      => Success(intType)
      case "double"                      => Success(doubleType)
      case "double precision"            => Success(doubleType)
      case "float8"                      => Success(doubleType)
      case "real"                        => Success(floatType)
      case "float4"                      => Success(floatType)
      case "decimal"                     => Success(decimalType)
      case "money"                       => Success(decimalType)
      case "numeric"                     => Success(decimalType)
      case "bytea"                       => Success(byteArrayType)
      case "character"                   => Success(stringType)
      case "character varying"           => Success(stringType)
      case "varchar"                     => Success(stringType)
      case "bpchar"                      => Success(stringType)
      case "text"                        => Success(stringType)
      case "date"                        => Success(localDateType)
      case "time"                        => Success(localTimeType)
      case "timetz"                      => Success(localTimeType)
      case "time with time zone"         => Success(localTimeType)
      case "timestamp"                   => Success(instantType)
      case "timestamptz"                 => Success(instantType)
      case "timestamp with time zone"    => Success(instantType)
      case "timestamp without time zone" => Success(instantType)
      case "uuid"                        => Success(uuidType)
      case "json"                        => Success(jsonType)
      case "jsonb"                       => Success(jsonType)
      case "inet"                        => Success(inetType)

      // Simple arrays of built-in types
      case typeToParse if typeToParse.startsWith("_") =>
        typeMapping(
          packagePrefix,
          typeToParse.replaceFirst("_", ""),
        ).map(tpe => ResolvedType(s"Array[${tpe.modelType}]", tpe.udt))

      // TODO UDTs and arrays od UDTs
      case _ =>
        Failure(
          new Exception(
            s"Mapping for $dbDataType not found!",
          ),
        )

    }
  }
}

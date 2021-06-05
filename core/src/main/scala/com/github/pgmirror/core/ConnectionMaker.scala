package com.github.pgmirror.core

import java.sql.DriverManager

object ConnectionMaker {
  def doAll(driver: String, url: String, user: String, pass: String): Unit = {
    Class.forName(driver)

    val conn = DriverManager.getConnection(url, user, pass)

//    val stmt = conn.prepareStatement(
//      """
//        |create table alltypes(
//        |  c1 bigint,
//        |  c2 int8,
//        |  c3 bigserial,
//        |  c4 boolean,
//        |  c5 bool,
//        |  c6 integer,
//        |  c7 int,
//        |  c8 int4,
//        |  c9 smallint,
//        |  c10 int2,
//        |  c12 serial,
//        |  c13 double precision,
//        |  c14 float8,
//        |  c15 real,
//        |  c16 float4,
//        |  c17 money,
//        |  c18 numeric,
//        |  c19 bytea,
//        |  c20 character,
//        |  c21 character varying,
//        |  c22 varchar,
//        |  c23 text,
//        |  c24 date,
//        |  c26 time with time zone,
//        |  c27 timestamp,
//        |  c29 timestamp with time zone,
//        |  c30 timestamp without time zone,
//        |  c31 uuid,
//        |  c32 json,
//        |  c34 ARRAY[200]);
//        |""".stripMargin,
//    )
//    stmt.execute()

    import model.database._

    val allTables = Table.getTables(conn)

    Column.getColumns(conn, "public", "alltypes").foreach(println)

  }
}

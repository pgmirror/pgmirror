package com.github.pgmirror.core

import java.sql.{Connection, DriverManager}

class ConnectionMaker {
  def doAll(driver: String, url: String, user: String, pass: String): Unit = {
    Class.forName(driver)

    val conn = DriverManager.getConnection(url, user, pass)

    import model.gatherer._

    val allTables = PgTable.getTables(conn)

    allTables.foreach{ t =>
      println(s"Analyzing table ${t.tableSchema}.${t.tableName}")
      val fks = PgForeignKey.getForTable(t.tableSchema, t.tableName)(conn)

      fks.foreach(println)
    }

  }
}

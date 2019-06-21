package com.github.irumiha.pgmirror

import java.sql.{Connection, DriverManager, ResultSet}

import com.github.irumiha.pgmirror.ResultSetIterator._
import com.github.irumiha.pgmirror.model.gatherer._
import com.github.irumiha.pgmirror.model.generator.Database

abstract class DatabaseSchemaGatherer(settings: Settings) {

  protected lazy val driverCls: Class[_] = Class.forName("org.postgresql.Driver")
  protected lazy val database: Connection = DriverManager.getConnection(settings.url, settings.user, settings.password)

  def loadDatabase: Database = {
    def runStatement[R](querySql: String, tr: ResultSet => R): List[R] = {
      val ps = database.prepareStatement(querySql)
      val list = ps.executeQuery().toIterator.map(tr).toList
      ps.close()
      list
    }

    val foreignKeys = runStatement(PgForeignKeys.sql, PgForeignKeys.fromResultSet)
    val tables = runStatement(PgTables.sql, PgTables.fromResultSet)
    val columns = runStatement(PgColumns.sql, PgColumns.fromResultSet)
    val udtAttributes = runStatement(PgUdtAttributes.sql, PgUdtAttributes.fromResultSet)

    Database(List(), List(), List())
  }
}

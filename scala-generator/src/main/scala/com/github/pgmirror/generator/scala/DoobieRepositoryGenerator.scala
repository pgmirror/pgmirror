package com.github.pgmirror.generator.scala

import com.github.pgmirror.core.model.generator.ColumnAnnotation._
import com.github.pgmirror.core.model.generator.TableAnnotation._
import com.github.pgmirror.core.model.generator._
import com.github.pgmirror.core.{GeneratedFile, Generator, Names, Settings}

import ScalaCommon._

import java.io.File

class DoobieRepositoryGenerator(settings: Settings) extends Generator(settings) {

  private val tq = "\"\"\""

  override def generateUtil: List[GeneratedFile] =
    List(
      GeneratedFile("repository", "DoobieRepository.scala", generateBaseRepository()),
    )

  private def generateBaseRepository(): String =
    s"""package ${settings.rootPackage}.repository
      |
      |import doobie._
      |import io.circe.{Decoder, Encoder}
      |import doobie.Read
      |
      |
      |abstract class DoobieRepository[E: Read : Encoder: Decoder, PK] {
      |  def tableColumns: List[String]
      |  val hasDefault: List[E => Boolean]
      |
      |  protected def columnsToInsert(item: E): List[String] =
      |    tableColumns
      |      .zip(hasDefault)
      |      .filterNot{ case (_, p) => p(item)}
      |      .map{ case (col, _) => col}
      |
      |  def insertSql(item: E): Fragment
      |  def insertAllColumnsSql(item: E): Fragment
      |  def getSql(pk: PK): Fragment
      |  def deleteSql(pk: PK): Fragment
      |  def updateSql(item: E): Fragment
      |
      |  def insertQuery(item: E): Query0[E] = insertSql(item).query[E]
      |  def insertAllColumnsQuery(item: E): Query0[E] = insertAllColumnsSql(item).query[E]
      |  def getQuery(pk: PK): Query0[E] = getSql(pk).query[E]
      |  def deleteQuery(pk: PK): Query0[E] = deleteSql(pk).query[E]
      |  def updateQuery(item: E): Query0[E] = updateSql(item).query[E]
      |
      |  def insert(item: E): ConnectionIO[E] =
      |    insertQuery(item).unique
      |
      |  def insertAllColumns(item: E): ConnectionIO[E] =
      |    insertAllColumnsQuery(item).unique
      |
      |  def get(pk: PK): ConnectionIO[Option[E]] =
      |    getQuery(pk).option
      |
      |  def delete(pk: PK): ConnectionIO[Option[E]] =
      |    deleteQuery(pk).option
      |
      |  def update(item: E): ConnectionIO[Option[E]] =
      |    updateQuery(item).option
      |}
      |""".stripMargin

  override def generateForTable(
    table: Table,
    foreignKeys: List[ForeignKey],
  ): List[GeneratedFile] = {
    System.out.println(s"Processing table: ${table.nameWithSchema}")

    // This repository works only for tables with a simple primary key.
    // If the table does not have a PK skip generating the repository.
    table.columns.find(_.isPrimaryKey).map { _ =>
      val repositoryPath =
        Seq(table.schemaName, "repository").filterNot(_.isEmpty).mkString(File.separator)

      val repository: String =
        generateTableRepository(settings, table)

      GeneratedFile(repositoryPath, Names.toClassCamelCase(table.name) + "Repository.scala", repository),
    }.toList
  }

  private def generateTableRepository(settings: Settings, table: Table): String = {
    val pkColumn = table.columns.find(_.isPrimaryKey)

    def selectScalaItems = {
      table.columns.map(tc => s"$${item.${scalaPropName(tc)}}").mkString(",")
    }

    val insertDef =
      s"""  override def insertSql(item: ${Names.toClassCamelCase(table.name)}): Fragment = {
         |    val insertInto =
         |      Fragment.const(
         |        columnsToInsert(item).mkString(${tq}insert into ${table.nameWithSchema} ($tq,", ", ") ")
         |      )
         |
         |    val values =
         |      Fragment.const(
         |        columnsToInsert(item).mkString("(select ", ", ", " ")
         |      )
         |
         |    val subselect =
         |      fr${tq}from (SELECT (___inner::${table.nameWithSchema}).* from (select $selectScalaItems) as ___inner) as __outer)$tq
         |
         |    val returning = fr${tq}returning ${table.columns.map(tableColumn).mkString(",")}$tq
         |
         |    insertInto ++ values ++ subselect ++ returning
         |  }
         |""".stripMargin

    val insertAllColumnsDef =
      s"""  override def insertAllColumnsSql(item: ${Names.toClassCamelCase(table.name)}): Fragment =
         |    sql$tq
         |      insert into ${table.nameWithSchema}
         |             (${table.columns.map(columnNameQuoted).mkString(",\n              ")})
         |             values
         |             (${table.columns.map(tc => s"$${item.${scalaPropName(tc)}}").mkString(",\n              ")})
         |      returning ${table.columns.map(tableColumn).mkString(",\n                ")}
         |    $tq
         |""".stripMargin

    val getDef = pkColumn
      .map { p =>
        s"""  override def getSql(${scalaProp(settings.rootPackage, p)}): Fragment =
         |    sql$tq
         |      select ${table.columns.map(columnNameQuoted).mkString(",\n             ")}
         |        from ${table.nameWithSchema}
         |       where ${tableColumn(p)} = $$${scalaPropName(p)}
         |    $tq
         |""".stripMargin
      }
      .getOrElse("")

    val finds = table.columns.filter(c => c.annotations.contains(Find)).map { c =>
      s"""  def findBy${scalaPropName(c).capitalize}Sql(${scalaProp(settings.rootPackage, c)}): Fragment =
         |    sql$tq
         |      select ${table.columns.map(columnNameQuoted).mkString(",\n             ")}
         |        from ${table.nameWithSchema}
         |       where ${tableColumn(c)} = $$${scalaPropName(c)}
         |    $tq
         |
         |  def findBy${scalaPropName(c).capitalize}(${scalaProp(settings.rootPackage, c)}): ConnectionIO[List[${Names.toClassCamelCase(table.name)}]] = {
         |    findBy${scalaPropName(c).capitalize}Sql(${scalaPropName(c)})
         |    .query[${Names.toClassCamelCase(table.name)}]
         |    .to[List]
         |  }
         |""".stripMargin
    }

    val findOnes = table.columns.filter(c => c.annotations.contains(FindOne)).map { c =>
      s"""  def findOneBy${scalaPropName(c).capitalize}Sql(${scalaProp(settings.rootPackage, c)}): Fragment =
         |    sql$tq
         |      select ${table.columns.map(columnNameQuoted).mkString(",\n             ")}
         |        from ${table.nameWithSchema}
         |       where ${tableColumn(c)} = $$${scalaPropName(c)}
         |    $tq
         |
         |  def findOneBy${scalaPropName(c).capitalize}(${scalaProp(settings.rootPackage, c)}): ConnectionIO[Option[${Names.toClassCamelCase(table.name)}]] = {
         |    findOneBy${scalaPropName(c).capitalize}Sql(${scalaPropName(c)}).query[${Names.toClassCamelCase(table.name)}]
         |    .option
         |  }
         |""".stripMargin
    }

    val deleteDef = pkColumn
      .map { p =>
        s"""  override def deleteSql(${scalaProp(settings.rootPackage, p)}): Fragment =
         |    sql$tq
         |      delete from ${table.nameWithSchema}
         |       where ${columnNameQuoted(p)} = $$${scalaPropName(p)}
         |      returning ${table.columns.map(columnNameQuoted).mkString(",\n                ")}
         |    $tq
         |""".stripMargin
      }
      .getOrElse("")

    val updateDef = pkColumn
      .map { p =>
        s"""  override def updateSql(item: ${Names.toClassCamelCase(table.name)}): Fragment =
         |    sql$tq
         |      update ${table.nameWithSchema}
         |         set ${table.columns
          .filterNot(_.isPrimaryKey)
          .map(tc => s"${columnNameQuoted(tc)} = $${item.${scalaPropName(tc)}}")
          .mkString(",\n             ")}
         |       where ${tableColumn(p)} = $${item.${scalaPropName(p)}}
         |      returning ${table.columns.map(columnNameQuoted).mkString(",\n                ")}
         |    $tq
         |""".stripMargin
      }
      .getOrElse("")

    val crudDefs =
      s"""$insertDef
         |$insertAllColumnsDef
         |$getDef
         |$deleteDef
         |$updateDef
         |${finds.mkString("\n")}
         |${findOnes.mkString("\n")}
         |""".stripMargin

    val generateHasDefaults =
      table.columns
        .map { c =>
          if (c.hasDefault)
            s"_.${scalaPropName(c)} == ${columnDefault(scalaPropType(settings.rootPackage, c))}"
          else
            "_ => false"
        }
        .mkString(",\n    ")

    s"""package ${tablePackage(settings.rootPackage, table.schemaName)}.repository
       |
       |${repositoryImports(table)}
       |
       |import ${tablePackage(settings.rootPackage, table.schemaName)}.${Names.toClassCamelCase(table.name)}
       |import ${settings.rootPackage}.repository.DoobieRepository
       |
       |trait ${Names.toClassCamelCase(table.name)}Repository extends DoobieRepository[${Names.toClassCamelCase(table.name)}, ${pkColumn.map(scalaPropType(settings.rootPackage, _)).getOrElse("Nothing")}] {
       |  val tableColumns = List(${table.columns.map(columnNameQuoted).mkString(",")})
       |
       |  val hasDefault: List[${Names.toClassCamelCase(table.name)} => Boolean] = List(
       |    $generateHasDefaults
       |  )
       |
       |$crudDefs
       |}
       |
       |object ${Names.toClassCamelCase(table.name)}DefaultRepository extends ${Names.toClassCamelCase(table.name)}Repository
       |
       """.stripMargin
  }

  override def generateForView(
    view: View,
  ): List[GeneratedFile] = {
    System.out.println(s"Processing view: ${view.nameWithSchema}")
    val repositoryPath = Seq(view.schemaName, "repository").filterNot(_.isEmpty).mkString(File.separator)

    val repository: String =
      generateViewRepository(settings, view)

    //noinspection DuplicatedCode
    List(
      GeneratedFile(repositoryPath, Names.toClassCamelCase(view.name) + "Repository.scala", repository),
    )
  }

  private def generateViewRepository(settings: Settings, view: View): String = {

    val filters: List[(String, String, String)] = for {
      col <- view.columns
      ann <- col.annotations.filter(ColumnAnnotation.filterValues.contains)
    } yield {
      val (paramName, paramNameVal) = ann match {
        case FilterEq   => (s"${scalaPropName(col)}_=",  s"${scalaPropName(col)}")
        case FilterLt   => (s"${scalaPropName(col)}_<",  s"${scalaPropName(col)}")
        case FilterGt   => (s"${scalaPropName(col)}_>",  s"${scalaPropName(col)}")
        case FilterGtEq => (s"${scalaPropName(col)}_>=", s"${scalaPropName(col)}")
        case FilterLtEq => (s"${scalaPropName(col)}_<=", s"${scalaPropName(col)}")
        case _          => throw new IllegalArgumentException("Only filter annotations allowed!")
      }

      val filterOp = ann match {
        case FilterEq   => "="
        case FilterLt   => "<"
        case FilterGt   => ">"
        case FilterGtEq => ">="
        case FilterLtEq => "<="
        case _          => throw new IllegalArgumentException("Only filter annotations allowed!")
      }

      (
        s"$paramName : Option[${scalaPropType(settings.rootPackage, col)}] = None,",
        s"""val ${paramNameVal}Filter = $paramName.map(v => fr"${col.name} $filterOp $$v")""",
        s"${paramNameVal}Filter",
      )
    }
    val (limitParam, limitFr, rLimitFr) =
      if (view.annotations.contains(Limit))
        (
          "      limit: Option[Int] = None,\n",
          "    val limitFr: Fragment = if (limit.isDefined) Fragment.const(s\"LIMIT $${limit.get}\") else Fragment.empty\n",
          " ++ limitFr",
        )
      else
        ("", "", "")

    val (offsetParam, offsetFr, rOffsetFr) =
      if (view.annotations.contains(Offset))
        (
          "      offset: Option[Int] = None\n",
          "    val offsetFr: Fragment = if (offset.isDefined) Fragment.const(s\"OFFSET $${offset.get}\") else Fragment.empty\n",
          " ++ offsetFr",
        )
      else
        ("", "", "")

    val (whereFr, rWhereFr) =
      if (filters.nonEmpty)
        (
          s"    val whereFr: Fragment = whereAndOpt(${filters.map(f => f._3).mkString(",")})\n",
          " ++ whereFr",
        )
      else
        ("", "")

    s"""package ${tablePackage(settings.rootPackage, view.schemaName)}.repository
       |
       |${repositoryImports(view)}
       |import Fragments.whereAndOpt
       |
       |import ${tablePackage(settings.rootPackage, view.schemaName)}.${Names.toClassCamelCase(view.name)}
       |
       |trait ${Names.toClassCamelCase(view.name)}Repository {
       |  def listFiltered(${filters
      .map(f => "      " + f._1)
      .mkString("\n", "\n", "\n")}$offsetParam$limitParam  ): Query0[${Names.toClassCamelCase(view.name)}] = {
       |${filters.map(f => "    " + f._2).mkString("\n")}
       |    val selectFr: Fragment =
       |      fr${tq}select ${view.columns
      .map(tableColumn)
      .mkString(",")} from ${view.nameWithSchema}$tq
       |$whereFr$offsetFr$limitFr
       |    val q: Fragment = selectFr$rWhereFr$rLimitFr$rOffsetFr
       |
       |    q.query[${Names.toClassCamelCase(view.name)}]
       |  }
       |}
       |
       |object ${Names.toClassCamelCase(view.name)}DefaultRepository extends ${Names
      .toClassCamelCase(view.name)}Repository
       |
       |""".stripMargin
  }

  private def repositoryImports(t: Columns): String = {
    val columnTypes = t.columns.map(scalaPropType(settings.rootPackage, _)).toSet

    val sb =
      new StringBuilder()
        .append("import doobie._\n")
        .append("|import doobie.implicits._\n")

    if (
      columnTypes.contains("java.util.UUID")
      || columnTypes.contains("Option[java.util.UUID]")
      || columnTypes.contains("Array[Int]")
      || columnTypes.contains("Option[Array[Int]]")
      || columnTypes.contains("Array[String]")
      || columnTypes.contains("Option[Array[String]]")
      || columnTypes.contains("java.net.InetAddress")
      || columnTypes.contains("Option[java.net.InetAddress]")
    ) {
      sb.append("|import doobie.postgres.implicits._\n")
    }

    if (
      columnTypes.contains("java.time.Instant")
      || columnTypes.contains("Option[java.time.Instant]")
      || columnTypes.contains("java.time.LocalDate")
      || columnTypes.contains("Option[java.time.LocalDate]")
      || columnTypes.contains("java.time.LocalTime")
      || columnTypes.contains("Option[java.time.LocalTime]")
    ) {
      sb.append("|import doobie.implicits.javatime._\n")
    }

    if (columnTypes.contains("io.circe.Json") || columnTypes.contains("Option[io.circe.Json]")) {
      sb.append("|import doobie.postgres.circe.jsonb.implicits._\n")
    }

    sb.mkString
  }
}

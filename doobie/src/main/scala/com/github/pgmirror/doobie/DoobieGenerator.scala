package com.github.pgmirror.doobie

import com.github.pgmirror.core.model.generator.ColumnAnnotation._
import com.github.pgmirror.core.model.generator.TableAnnotation._
import com.github.pgmirror.core.model.generator.{Column, ColumnAnnotation, ForeignKey, TableLike}
import com.github.pgmirror.core.{GeneratedFile, Generator, Settings}

class DoobieGenerator(settings: Settings) extends Generator(settings) {

  override def generateUtil: Option[GeneratedFile] = Some {
    GeneratedFile(
      "repositories/doobie",
      "DoobieRepository.scala",
      generateBaseRepository()
    )
  }

  override def generateForTable(table: TableLike, foreignKeys: List[ForeignKey]): List[GeneratedFile] = {
    System.out.println(s"Processing: ${table.tableWithSchema}")
    val repositoryPath =
      if (table.isView)
        Seq(table.schemaName, "repositories", "doobie", "views").filterNot(_.isEmpty).mkString("/")
      else
        Seq(table.schemaName, "repositories", "doobie", "models").filterNot(_.isEmpty).mkString("/")

    val modelPath =
      if (table.isView)
        Seq(table.schemaName, "views").filterNot(_.isEmpty).mkString("/")
      else
        Seq(table.schemaName, "models").filterNot(_.isEmpty).mkString("/")

    val repository: String =
      if (table.isView)
        generateViewRepository(settings, table)
      else
        generateTableRepository(settings, table)

    List(
      GeneratedFile(modelPath, table.className + ".scala", generateTableClass(settings, table)),
      GeneratedFile(repositoryPath, table.className + "Repository.scala", repository)
    )
  }

  private val tq = "\"\"\""

  private def tablePackage(rootPackage: String, schemaName: String): String =
    List(rootPackage, schemaName).filterNot(_.isEmpty).mkString(".")

  private def generateTableClass(settings: Settings, table: TableLike): String = {
    val pkColumn = table.columns.find(_.isPrimaryKey)
    val packageSuffix =
      if (table.isView)
        "views"
      else
        "models"

    val uuidPkZero    = "new UUID(0,0)"
    val numericPkZero = "0"
    val stringPkZero  = "\"\""

    def pkZeroDefault(column: Column) =
      column.propWithComment + " = " + (table.columns.filter(_.isPrimaryKey).head.propType match {
      case "Int"            => numericPkZero
      case "Long"           => numericPkZero
      case "java.util.UUID" => uuidPkZero
      case "String"         => stringPkZero
      case t                => throw new UnsupportedOperationException(s"Unsupported primary key type: $t")
    })

    val columnList =
      if (pkColumn.exists(_.annotations.contains(AppPk))) {
        table.columns.map(_.propWithComment).mkString(",\n  ")
      }
      else {
        table.columns.map(c => if (c.isPrimaryKey) pkZeroDefault(c) else c.propWithComment).mkString(",\n  ")
      }

    s"""package ${tablePackage(settings.rootPackage, table.schemaName)}.$packageSuffix
       |
       |import io.circe.{Decoder, Encoder}
       |import io.circe.generic.extras.semiauto._
       |import io.circe.generic.extras.Configuration
       |
       |case class ${table.className} (
       |  $columnList
       |)
       |
       |object ${table.className} {
       |  implicit val customConfig: Configuration = Configuration.default.withSnakeCaseMemberNames
       |
       |  implicit val decode${table.className}: Decoder[${table.className}] = deriveConfiguredDecoder
       |
       |  implicit val encode${table.className}: Encoder[${table.className}] = deriveConfiguredEncoder
       |
       |}
       |
       |""".stripMargin
  }

  private def generateTableRepository(settings: Settings, table: TableLike): String = {
    val pkColumn = table.columns.find(_.isPrimaryKey)
    def appPk = pkColumn.exists(_.annotations.contains(AppPk))

    val insertDefAppPk =
      s"""  override def insertSql(item: ${table.className}): Fragment =
         |    sql$tq
         |      insert into ${table.tableWithSchema}
         |             (${table.columns.map(_.columnNameQuoted).mkString(",\n              ")})
         |             values
         |             (${table.columns.map(tc => s"$${item.${tc.propName}}").mkString(",\n              ")})
         |      returning ${table.columns.map(_.tableColumn).mkString(",\n                ")}
         |    $tq
         |""".stripMargin

    val insertDefAutoPk =
      s"""  override def insertSql(item: ${table.className}): Fragment =
         |    sql$tq
         |      insert into ${table.tableWithSchema}
         |             (${table.columns.filterNot(_.isPrimaryKey).map(_.columnNameQuoted).mkString(",\n              ")})
         |             values
         |             (${table.columns.filterNot(_.isPrimaryKey).map(tc => s"$${item.${tc.propName}}").mkString(",\n              ")})
         |      returning ${table.columns.map(_.tableColumn).mkString(",\n                ")}
         |    $tq
         |""".stripMargin


    val getDef = pkColumn.map { p =>
      s"""  override def getSql(${p.prop}): Fragment =
         |    sql$tq
         |      select ${table.columns.map(_.columnNameQuoted).mkString(",\n             ")}
         |        from ${table.tableWithSchema}
         |       where ${p.tableColumn} = $$${p.propName}
         |    $tq
         |""".stripMargin
    }.getOrElse("")

    val finds = table.columns.filter(c => c.annotations.contains(Find)).map { c =>
      s"""  def findBy${c.propName.capitalize}Sql(${c.prop}): Fragment =
         |    sql$tq
         |      select ${table.columns.map(_.columnNameQuoted).mkString(",\n             ")}
         |        from ${table.tableWithSchema}
         |       where ${c.tableColumn} = $$${c.propName}
         |    $tq
         |
         |  def findBy${c.propName.capitalize}(${c.prop}): ConnectionIO[List[${table.className}]] = {
         |    findBy${c.propName.capitalize}Sql(${c.propName})
         |    .query[${table.className}]
         |    .to[List]
         |  }
         |""".stripMargin
    }

    val findOnes = table.columns.filter(c => c.annotations.contains(FindOne)).map { c =>
      s"""  def findOneBy${c.propName.capitalize}Sql(${c.prop}): Fragment =
         |    sql$tq
         |      select ${table.columns.map(_.columnNameQuoted).mkString(",\n             ")}
         |        from ${table.tableWithSchema}
         |       where ${c.tableColumn} = $$${c.propName}
         |    $tq
         |
         |  def findOneBy${c.propName.capitalize}(${c.prop}): ConnectionIO[Option[${table.className}]] = {
         |    findOneBy${c.propName.capitalize}Sql(${c.propName}).query[${table.className}]
         |    .option
         |  }
         |""".stripMargin
    }

    val deleteDef = pkColumn.map { p =>
      s"""  override def deleteSql(${p.prop}): Fragment =
         |    sql$tq
         |      delete from ${table.tableWithSchema}
         |       where ${p.columnNameQuoted} = $$${p.propName}
         |      returning ${table.columns.map(_.columnNameQuoted).mkString(",\n                ")}
         |    $tq
         |""".stripMargin
    }.getOrElse("")


    val updateDef = pkColumn.map { p =>
      s"""  def updateSql(item: ${table.className}): Fragment =
         |    sql$tq
         |      update ${table.tableWithSchema}
         |         set ${table.columns.filterNot(_.isPrimaryKey).map(tc => s"${tc.columnNameQuoted} = $${item.${tc.propName}}").mkString(",\n             ")}
         |       where ${p.tableColumn} = $${item.${p.propName}}
         |      returning ${table.columns.map(_.columnNameQuoted).mkString(",\n                ")}
         |    $tq
         |""".stripMargin
    }.getOrElse("")

    val crudDefs =
      s"""${if (appPk) insertDefAppPk else insertDefAutoPk}
         |$getDef
         |$deleteDef
         |$updateDef
         |${finds.mkString("\n")}
         |${findOnes.mkString("\n")}
         |""".stripMargin

    s"""package ${tablePackage(settings.rootPackage, table.schemaName)}.repositories.doobie.models
       |
       |import doobie._
       |import doobie.implicits._
       |import doobie.postgres.implicits._
       |
       |import java.util.UUID
       |import java.time.Instant
       |
       |import ${tablePackage(settings.rootPackage, table.schemaName)}.models.${table.className}
       |import ${settings.rootPackage}.repositories.doobie.DoobieRepository
       |
       |class ${table.className}Repository extends DoobieRepository[${table.className}, ${pkColumn.get.propType}] {
       |$crudDefs
       |}
       """.stripMargin
  }

  private def generateViewRepository(settings: Settings, view: TableLike): String = {

    val filters: List[(String, String, String)] = for {
      col <- view.columns
      ann <- col.annotations.filter(ColumnAnnotation.filterValues.contains)
    } yield {
      val (paramName, paramNameVal) = ann match {
        case FilterEq   => (s"${col.propName}_=", s"${col.propName}")
        case FilterLt   => (s"${col.propName}_<", s"${col.propName}")
        case FilterGt   => (s"${col.propName}_>", s"${col.propName}")
        case FilterGtEq => (s"${col.propName}_>=", s"${col.propName}")
        case FilterLtEq => (s"${col.propName}_<=", s"${col.propName}")
        case _ => throw new IllegalArgumentException("Only filter annotations allowed!")
      }

      val filterOp = ann match {
        case FilterEq => "="
        case FilterLt => "<"
        case FilterGt => ">"
        case FilterGtEq => ">="
        case FilterLtEq => "<="
        case _ => throw new IllegalArgumentException("Only filter annotations allowed!")
      }

      (
        s"$paramName : Option[${col.propType}] = None,",
        s"""val ${paramNameVal}Filter = $paramName.map(v => fr"${col.name} $filterOp $$v")""",
        s"${paramNameVal}Filter"
      )
    }
    val (limitParam, limitFr, rLimitFr) =
      if (view.annotations.contains(Limit))
        (
          "      limit: Option[Int] = None,\n",
          "    val limitFr: Fragment = if (limit.isDefined) Fragment.const(s\"LIMIT $${limit.get}\") else Fragment.empty\n",
          " ++ limitFr"
        )
      else
        ("", "", "")

    val (offsetParam, offsetFr, rOffsetFr) =
      if (view.annotations.contains(Offset))
        (
          "      offset: Option[Int] = None\n",
          "    val offsetFr: Fragment = if (offset.isDefined) Fragment.const(s\"OFFSET $${offset.get}\") else Fragment.empty\n",
          " ++ offsetFr"
        )
      else
        ("", "", "")

    val (whereFr, rWhereFr) =
      if (filters.nonEmpty)
        (
          s"    val whereFr: Fragment = whereAndOpt(${filters.map(f => f._3).mkString(",")})\n",
          " ++ whereFr"
        )
      else
        ("", "")

    s"""
       |package ${tablePackage(settings.rootPackage, view.schemaName)}.repositories.doobie.views
       |
       |import doobie._
       |import doobie.implicits._
       |import doobie.postgres.implicits._
       |import Fragments.{ in, whereAndOpt }
       |
       |import java.util.UUID
       |import java.time.Instant
       |
       |import ${tablePackage(settings.rootPackage, view.schemaName)}.views.${view.className}
       |
       |class ${view.className}DoobieRepository {
       |  def listFiltered(${filters.map(f => "      " + f._1).mkString("\n","\n","\n")}$offsetParam$limitParam  ): Query0[${view.className}] = {
       |${filters.map(f => "    " + f._2).mkString("\n")}
       |    val selectFr: Fragment =
       |      fr${tq}select ${view.columns.map(_.tableColumn).mkString(",")} from ${view.tableWithSchema}$tq
       |$whereFr$offsetFr$limitFr
       |    val q: Fragment = selectFr${rWhereFr}${rLimitFr}${rOffsetFr}
       |
       |    q.query[${view.className}]
       |  }
       |}
       |
       |""".stripMargin
  }

  private def generateBaseRepository(): String =
    s"""package ${settings.rootPackage}.repositories.doobie
      |
      |import doobie._
      |import io.circe.{Decoder, Encoder}
      |import doobie.Read
      |
      |
      |abstract class DoobieRepository[E: Read : Encoder: Decoder, PK] {
      |  def insertSql(item: E): Fragment = Fragment.empty
      |  def getSql(pk: PK): Fragment = Fragment.empty
      |  def deleteSql(pk: PK): Fragment = Fragment.empty
      |  def updateSql(item: E): Fragment = Fragment.empty
      |
      |  def insertQery(item: E): Query0[E] = insertSql(item).query[E]
      |  def getQuery(pk: PK): Query0[E] = getSql(pk).query[E]
      |  def deleteQuery(pk: PK): Query0[E] = deleteSql(pk).query[E]
      |  def updateQuery(item: E): Query0[E] = updateSql(item).query[E]
      |
      |  def insert(item: E): ConnectionIO[E] =
      |    insertQery(item).unique
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
}

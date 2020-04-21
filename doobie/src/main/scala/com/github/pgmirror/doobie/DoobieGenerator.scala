package com.github.pgmirror.doobie

import com.github.pgmirror.core.model.generator.ColumnAnnotation._
import com.github.pgmirror.core.model.generator.TableAnnotation._
import com.github.pgmirror.core.model.generator.{Column, ColumnAnnotation, ForeignKey, TableLike}
import com.github.pgmirror.core.{GeneratedFile, Generator, Settings}

class DoobieGenerator(settings: Settings) extends Generator(settings) {

  override def generateUtil: Option[GeneratedFile] = Some {
    GeneratedFile("repository", "DoobieRepository.scala", generateBaseRepository())
  }

  override def generateForTable(table: TableLike, foreignKeys: List[ForeignKey]): List[GeneratedFile] = {
    System.out.println(s"Processing: ${table.tableWithSchema}")
    val repositoryPath = Seq(table.schemaName, "repository").filterNot(_.isEmpty).mkString("/")
    val modelPath = Seq(table.schemaName).filterNot(_.isEmpty).mkString("/")

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

  private def columnDefault(p: String) = p match {
    case "Int"                     => "Int.MinValue"
    case "Long"                    => "Long.MinValue"
    case "Float"                   => "Float.NaN"
    case "Double"                  => "Double.NaN"
    case "BigDecimal"              => "BigDecimal(0)"
    case "Boolean"                 => "false"
    case "Array[Byte]"             => "Array[Byte]()"
    case "String"                  => "\"\""
    case "java.util.UUID"          => "new java.util.UUID(0,0)"
    case "java.time.LocalDate"     => "java.time.LocalDate.MIN"
    case "java.time.LocalTime"     => "java.time.LocalTime.MIN"
    case "java.time.Instant"       => "java.time.Instant.MIN"
    case "java.time.LocalDateTime" => "java.time.LocalDateTime.MIN"
    case t if t.startsWith("Array[") => s"$t()"
    case t                => throw new UnsupportedOperationException(s"Unsupported primary key type: $t")
  }

  private def repositoryImports(t: TableLike): String = {
    val columnTypes = t.columns.map(_.propType).toSet

    val sb =
      new StringBuilder()
      .append("import doobie._\n")
      .append("|import doobie.implicits._\n")

    if (   columnTypes.contains("java.util.UUID")
        || columnTypes.contains("Option[java.util.UUID]")) {
      sb.append("|import doobie.postgres.implicits._\n")
    }

    if (  columnTypes.contains("java.time.Instant")
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

  private def generateTableClass(settings: Settings, table: TableLike): String = {

    def columnWithDefault(column: Column) =
      column.propWithComment + " = " + columnDefault(column.propType)

    val columnList =
      table.columns.map(c => if (c.hasDefault) columnWithDefault(c) else c.propWithComment).mkString(",\n  ")

    s"""package ${tablePackage(settings.rootPackage, table.schemaName)}
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
       |
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


    val insertDef =
      s"""  override def insertSql(item: ${table.className}): Fragment = {
         |    val insertInto =
         |      Fragment.const(
         |        columnsToInsert(item).mkString(${tq}insert into ${table.tableWithSchema} (${tq},", ", ") ")
         |      )
         |
         |    val values =
         |      Fragment.const(
         |        columnsToInsert(item).mkString("(select ", ", ", " ")
         |      )
         |
         |    val subselect =
         |      fr${tq}from (SELECT (___inner::${table.tableWithSchema}).* from (select ${table.columns.map(tc => s"$${item.${tc.propName}}").mkString(",")}) as ___inner) as __outer)${tq}
         |
         |    val returning = fr${tq}returning ${table.columns.map(_.tableColumn).mkString(",")}${tq}
         |
         |    insertInto ++ values ++ subselect ++ returning
         |  }
         |""".stripMargin

    val insertAllDef =
      s"""  override def insertAllSql(item: ${table.className}): Fragment =
         |    sql$tq
         |      insert into ${table.tableWithSchema}
         |             (${table.columns.map(_.columnNameQuoted).mkString(",\n              ")})
         |             values
         |             (${table.columns.map(tc => s"$${item.${tc.propName}}").mkString(",\n              ")})
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
      s"""  override def updateSql(item: ${table.className}): Fragment =
         |    sql$tq
         |      update ${table.tableWithSchema}
         |         set ${table.columns.filterNot(_.isPrimaryKey).map(tc => s"${tc.columnNameQuoted} = $${item.${tc.propName}}").mkString(",\n             ")}
         |       where ${p.tableColumn} = $${item.${p.propName}}
         |      returning ${table.columns.map(_.columnNameQuoted).mkString(",\n                ")}
         |    $tq
         |""".stripMargin
    }.getOrElse("")

    val crudDefs =
      s"""$insertDef
         |$insertAllDef
         |$getDef
         |$deleteDef
         |$updateDef
         |${finds.mkString("\n")}
         |${findOnes.mkString("\n")}
         |""".stripMargin

    val generateHasDefaults =
      table.columns.map{ c =>
        if (c.hasDefault)
          s"_.${c.propName} == ${columnDefault(c.propType)}"
        else
          "_ => false"
      }.mkString(",\n    ")

    s"""package ${tablePackage(settings.rootPackage, table.schemaName)}.repository
       |
       |${repositoryImports(table)}
       |
       |import ${tablePackage(settings.rootPackage, table.schemaName)}.${table.className}
       |import ${settings.rootPackage}.repository.DoobieRepository
       |
       |trait ${table.className}Repository extends DoobieRepository[${table.className}, ${pkColumn.get.propType}] {
       |  val tableColumns = List(${table.columns.map(_.columnNameQuoted).mkString(",")})
       |
       |  val hasDefault: List[${table.className} => Boolean] = List(
       |    $generateHasDefaults
       |  )
       |
       |$crudDefs
       |}
       |
       |object ${table.className}DefaultRepository extends ${table.className}Repository
       |
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

    s"""package ${tablePackage(settings.rootPackage, view.schemaName)}.repository
       |
       |${repositoryImports(view)}
       |import Fragments.whereAndOpt
       |
       |import ${tablePackage(settings.rootPackage, view.schemaName)}.${view.className}
       |
       |trait ${view.className}Repository {
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
       |object ${view.className}DefaultRepository extends ${view.className}Repository
       |
       |""".stripMargin
  }

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
      |  def insertAllSql(item: E): Fragment
      |  def getSql(pk: PK): Fragment
      |  def deleteSql(pk: PK): Fragment
      |  def updateSql(item: E): Fragment
      |
      |  def insertQuery(item: E): Query0[E] = insertSql(item).query[E]
      |  def insertAllQuery(item: E): Query0[E] = insertAllSql(item).query[E]
      |  def getQuery(pk: PK): Query0[E] = getSql(pk).query[E]
      |  def deleteQuery(pk: PK): Query0[E] = deleteSql(pk).query[E]
      |  def updateQuery(item: E): Query0[E] = updateSql(item).query[E]
      |
      |  def insert(item: E): ConnectionIO[E] =
      |    insertQuery(item).unique
      |
      |  def insertAllValues(item: E): ConnectionIO[E] =
      |    insertAllQuery(item).unique
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

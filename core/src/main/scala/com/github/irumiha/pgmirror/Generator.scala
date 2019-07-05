package com.github.irumiha.pgmirror

import java.nio.charset.Charset

import better.files.File
import com.github.irumiha.pgmirror.model.generator.{ForeignKey, TableLike}
import org.fusesource.scalate.{TemplateEngine, TemplateSource}

/**
 * A trait for source code generators.
 */
trait Generator {
  /**
   * Generates source code.
   */
  def generate(settings: Settings, tables: List[TableLike]): Unit

}

case class GeneratedFile(relativePath: String, filename: String, content: String)

/**
 * A simplest base class for source code generators.
 */
abstract class GeneratorBase extends Generator {

  /**
   * Generates source files.
   */
  def generate(settings: Settings, tables: List[TableLike], foreignKeys: List[ForeignKey]): Unit = {
    import settings._

    val rootOutputDir = rootPackage match {
      case "" => File(rootPath)
      case _  => File(s"$rootPath/${rootPackage.replace(".", "/")}")
    }

    rootOutputDir.createDirectories()

    tables.foreach { table =>
      val sources = generate(settings, table, foreignKeys)

      sources.foreach { ts =>
        rootOutputDir / ""
      }

      val file = rootOutputDir / (table.className + ".scala")
      file.write(source)(charset = Charset.forName(settings.charset))
    }
  }

  def generate(settings: Settings, table: TableLike, foreignKeys: List[ForeignKey]): List[GeneratedFile]

}

/**
 * Provides Scalate support for Generator implementations.
 *
 * Generators can render a Scalate template by render() method.
 */
trait ScalateSupport {

  /**
   * Renders a template with given attributes.
   */
  protected def render(settings: Settings, template: TemplateSource, attributes: Map[String, Any]): String = {
    val engine = new TemplateEngine
    engine.allowCaching =  false

    engine.layout(template, attributes)
  }

}

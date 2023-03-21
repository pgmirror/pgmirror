package com.github.pgmirror.core.util
// Copied from: https://github.com/sbt/sbt/blob/develop/internal/util-collection/src/main/scala/sbt/internal/util/Dag.scala

/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

//noinspection ScalaUnusedSymbol
trait Dag[Node <: Dag[Node]] { self: Node =>

  def dependencies: Iterable[Node]
  def topologicalSort = Dag.topologicalSort(self)(_.dependencies)
}

//noinspection ScalaWeakerAccess,ScalaUnusedSymbol
object Dag {

  import scala.collection.{mutable, JavaConverters}
  import JavaConverters.asScalaSetConverter

  def topologicalSort[T](root: T)(dependencies: T => Iterable[T]): List[T] =
    topologicalSort(root :: Nil)(dependencies)

  def topologicalSort[T](nodes: Iterable[T])(dependencies: T => Iterable[T]): List[T] = {
    val discovered = new mutable.HashSet[T]
    val finished = new java.util.LinkedHashSet[T].asScala

    def visitAll(nodes: Iterable[T]) = nodes.foreach(visit)

    def visit(node: T): Unit = {
      if (!discovered(node)) {
        discovered(node) = true
        try {
          visitAll(dependencies(node))
        } catch {
          case c: Cyclic => throw node :: c
        }
        finished += node
        ()
      } else if (!finished(node))
        throw new Cyclic(node)
    }

    visitAll(nodes)

    finished.toList
  }

  // doesn't check for cycles
  def topologicalSortUnchecked[T](node: T)(dependencies: T => Iterable[T]): List[T] =
    topologicalSortUnchecked(node :: Nil)(dependencies)

  def topologicalSortUnchecked[T](nodes: Iterable[T])(dependencies: T => Iterable[T]): List[T] = {
    val discovered = new mutable.HashSet[T]
    var finished: List[T] = Nil

    def visitAll(nodes: Iterable[T]) = nodes.foreach(visit)

    def visit(node: T): Unit = {
      if (!discovered(node)) {
        discovered(node) = true
        visitAll(dependencies(node))
        finished ::= node
      }
    }

    visitAll(nodes)
    finished
  }

  final class Cyclic(val value: Any, val all: List[Any], val complete: Boolean)
      extends Exception(
        "Cyclic reference involving " +
          (if (complete) all.mkString("\n   ", "\n   ", "") else value),
      ) {
    def this(value: Any) = this(value, value :: Nil, false)

    override def toString = getMessage

    def ::(a: Any): Cyclic =
      if (complete)
        this
      else if (a == value)
        new Cyclic(value, all, true)
      else
        new Cyclic(value, a :: all, false)
  }
}

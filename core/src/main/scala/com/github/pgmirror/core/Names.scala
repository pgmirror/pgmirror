package com.github.pgmirror.core

object Names {

  def camelCaseize(pgName: String): String =
    pgName
      .split("_")
      .filterNot(_.isEmpty)
      .map(_.capitalize)
      .mkString

}

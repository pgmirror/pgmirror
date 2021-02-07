package com.github.pgmirror.core

object Names {

  def toClassCamelCase(name: String): String = {
    val nameParts: Array[String] = name.split("_")

    if (nameParts.length > 1) {
      nameParts.map(_.capitalize).mkString
    } else {
      name.capitalize
    }
  }

  def toPropertyCamelCase(name: String): String = {
    val nameParts: Array[String] = name.split("_")

    if (nameParts.length > 1) {
      nameParts.head.toLowerCase + nameParts.tail.map(_.capitalize).mkString
    } else {
      name
    }
  }

}

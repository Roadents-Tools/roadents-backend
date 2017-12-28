package com.reroute.backend.logic

trait ApplicationRequestScala {
  val tag: String
}

trait RequestMapper[T <: ApplicationRequestScala] {
  def buildQuery(callArgs: Map[String, String]): Either[String, T]
}

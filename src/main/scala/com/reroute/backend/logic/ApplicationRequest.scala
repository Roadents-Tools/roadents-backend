package com.reroute.backend.logic

trait ApplicationRequest {
  val tag: String
}

trait RequestMapper[T <: ApplicationRequest] {
  def buildQuery(callArgs: Map[String, String]): Either[String, T]
}

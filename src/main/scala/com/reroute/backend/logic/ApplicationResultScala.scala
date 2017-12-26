package com.reroute.backend.logic

import com.reroute.backend.model.routing.RouteScala

sealed trait ApplicationResultScala {
  val routes: Seq[RouteScala] = Seq.empty

  val errors: Seq[String] = Seq.empty
}

object ApplicationResultScala {

  case class Result(override val routes: Seq[RouteScala]) extends ApplicationResultScala

  case class Error(override val errors: Seq[String]) extends ApplicationResultScala

}

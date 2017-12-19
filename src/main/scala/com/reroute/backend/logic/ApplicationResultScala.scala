package com.reroute.backend.logic

import com.reroute.backend.model.routing.RouteScala

sealed trait ApplicationResultScala {
  val routes: List[RouteScala] = List.empty

  val errors: List[String] = List.empty
}

object ApplicationResultScala {

  case class Result(override val routes: List[RouteScala]) extends ApplicationResultScala

  case class Error(override val errors: List[String]) extends ApplicationResultScala

}

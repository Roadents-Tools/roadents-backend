package com.reroute.backend.logic

import com.reroute.backend.model.routing.Route

sealed trait ApplicationResult {
  val routes: Seq[Route] = Seq.empty

  val errors: Seq[String] = Seq.empty
}

object ApplicationResult {

  case class Result(override val routes: Seq[Route]) extends ApplicationResult

  case class Error(override val errors: Seq[String]) extends ApplicationResult

}

package com.reroute.backend.logic.interfaces

import com.reroute.backend.logic.{ApplicationRequest, ApplicationResult}

trait LogicCore[T <: ApplicationRequest] {
  val tag: String

  def runLogic(request: T): ApplicationResult

  def isValid(request: T): Boolean
}

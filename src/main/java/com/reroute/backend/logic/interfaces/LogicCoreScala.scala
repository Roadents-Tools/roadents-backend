package com.reroute.backend.logic.interfaces

import com.reroute.backend.logic.{ApplicationRequestScala, ApplicationResultScala}

trait LogicCoreScala[T <: ApplicationRequestScala] {
  val tag: String

  def runLogic(request: T): ApplicationResultScala

  def isValid(request: T): Boolean
}

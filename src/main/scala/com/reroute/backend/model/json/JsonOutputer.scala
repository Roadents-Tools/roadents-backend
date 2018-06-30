package com.reroute.backend.model.json

trait JsonOutputer[T] {
  def output(inputObject: T): String
}

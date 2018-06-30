package com.reroute.backend.model.json

trait JsonSerializer[T] extends JsonOutputer[T] {

  def serialize(obj: T): String

  def deserialize(json: String): T

  override def output(inputObject: T): String = serialize(inputObject)

}

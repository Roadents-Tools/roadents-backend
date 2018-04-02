package com.reroute.displayers.restcontroller

import com.reroute.backend.logic.demo.DemoLogicCore
import com.reroute.backend.logic.donut.DonutCore
import com.reroute.backend.logic.finderdemo.FinderDemoCore
import com.reroute.backend.logic.interfaces.LogicCore
import com.reroute.backend.logic.revdonut.RevDonutCore
import com.reroute.backend.logic.{ApplicationRequest, ApplicationResult, RequestMapper}
import com.reroute.backend.model.json.RouteJsonOutputer
import com.typesafe.scalalogging.Logger
import spark.{Request, Response, Spark}

import scala.collection.JavaConverters._

object SparkHandler {

  private final val logger = Logger[SparkHandler.type]

  val paths: Seq[SparkArg[_ <: ApplicationRequest]] = Seq(
    SparkArg("/generator", DonutCore),
    SparkArg("/demo", DemoLogicCore),
    SparkArg("/funnel", RevDonutCore),
    SparkArg("/finderdemo", FinderDemoCore)
  )

  def main(args: Array[String]): Unit = {
    paths.foreach(arg => Spark.get(arg.path, runPath(arg, _, _)))
    Spark.before((_, response) => {
      response.header("Access-Control-Allow-Origin", "*")
    })
  }

  def runPath[T <: ApplicationRequest](arg: SparkArg[T], req: Request, res: Response): String = {
    res.`type`("Application/JSON")
    val params = req.queryMap.toMap.asScala.map(params => params._1 -> params._2(0)).toMap
    val logicReq = arg.parser.buildQuery(params) match {
      case Right(good) => good
      case Left(err) =>
        res.status(400)
        logger.error(s"Got error: $err")
        return s"""{ "error" : 400, "message" : "${err.replace("\"", "\\\"")}" }"""
    }
    arg.core.runLogic(logicReq) match {
      case ApplicationResult.Result(items) =>
        res.status(200)
        items.map(RouteJsonOutputer.output).mkString("[", ", ", "]")
      case ApplicationResult.Error(errs) =>
        res.status(500)
        logger.error(s"Got errors: ${errs.mkString("\n>>>   ")}")
        errs.mkString("""{ "error" : 500, "message" : " """, ",  ", " \"} ")
    }
  }
}

case class SparkArg[T <: ApplicationRequest](path: String, core: LogicCore[T])(implicit val parser: RequestMapper[T])
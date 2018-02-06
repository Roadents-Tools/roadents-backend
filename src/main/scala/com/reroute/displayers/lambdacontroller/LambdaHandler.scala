package com.reroute.displayers.lambdacontroller

import java.io._

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.reroute.backend.logic.demo.{DemoLogicCore, DemoRequest}
import com.reroute.backend.logic.donut.{DonutCore, DonutRequest}
import com.reroute.backend.logic.interfaces.LogicCore
import com.reroute.backend.logic.{ApplicationRequest, ApplicationResult, RequestMapper}
import com.reroute.backend.model.json.RouteJsonOutputer
import com.typesafe.scalalogging.Logger
import org.json.JSONObject

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object LambdaHandler {

  private final val logger = Logger[LambdaHandler.type]

  private def parseUrlArgs(inputStream: InputStream): Map[String, String] = {
    val reader = new BufferedReader(new InputStreamReader(inputStream))
    val inputStringBuilder = reader.lines.iterator().asScala.mkString
    logger.info(s"Got string $inputStringBuilder")
    val event = new JSONObject(inputStringBuilder.toString)
    val qps = Try(event.getJSONObject("queryStringParameters"))
    qps match {
      case Success(params) => params.keys.asScala
        .map({ case s: String => s -> params.getString(s) })
        .toMap
      case Failure(_) => Map.empty
    }
  }

  @throws[IOException]
  private def outputData(rawOutputJson: String, outputStream: OutputStream): Unit = {
    val responseJson = new JSONObject
    val headerJson = new JSONObject
    headerJson.put("content", "application/json")
    headerJson.put("access-control-allow-origin", "*")
    responseJson.put("headers", headerJson)
    responseJson.put("statusCode", "200")

    responseJson.put("body", rawOutputJson)

    logger.info(s"Outputting\n ${responseJson.toString(3)}")
    val writer = new OutputStreamWriter(outputStream)
    writer.write(responseJson.toString)
    writer.close()
  }

  @throws[IOException]
  private def outputError(code: Int, error: String, outputStream: OutputStream): Unit = {

    val bodyObj = new JSONObject()
      .put("error", code)
      .put("message", error)

    val headerJson = new JSONObject()
      .put("content", "application/json")

    val responseJson = new JSONObject()
      .put("statusCode", code)
      .put("headers", headerJson)
      .put("body", bodyObj)
    val writer = new OutputStreamWriter(outputStream)
    writer.write(responseJson.toString)
    writer.close()
  }
}

class LambdaHandler[U <: ApplicationRequest](val core: LogicCore[U], val mapper: RequestMapper[U])
  extends RequestStreamHandler {

  @throws[IOException]
  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    val urlArgs = LambdaHandler.parseUrlArgs(input)
    val algoReq = mapper.buildQuery(urlArgs)
    val res = algoReq match {
      case Right(req) => core.runLogic(req)
      case Left(err) =>
        LambdaHandler.outputError(400, err, output)
        return
    }
    res match {
      case ApplicationResult.Result(resList) =>
        val resJson = resList.map(RouteJsonOutputer.output).mkString("[", ", ", "]")
        LambdaHandler.outputData(resJson, output)
      case ApplicationResult.Error(errList) =>
        val resJson = errList.mkString(". In addition: ")
        LambdaHandler.outputError(500, resJson, output)
    }
  }
}

case class DonutLambdaHandler() extends LambdaHandler(DonutCore, DonutRequest)

case class DemoLambdaHandler() extends LambdaHandler(DemoLogicCore, DemoRequest)


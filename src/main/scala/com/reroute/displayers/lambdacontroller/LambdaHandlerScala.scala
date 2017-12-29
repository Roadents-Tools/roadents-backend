package com.reroute.displayers.lambdacontroller

import java.io._

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.reroute.backend.logic.donut.{DonutCore, DonutRequest}
import com.reroute.backend.logic.interfaces.LogicCoreScala
import com.reroute.backend.logic.{ApplicationRequestScala, ApplicationResultScala, RequestMapper}
import com.reroute.backend.model.json.RouteJsonOutputer
import org.json.JSONObject

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object LambdaHandlerScala {

  private def parseUrlArgs(inputStream: InputStream): Map[String, String] = {
    val reader = new BufferedReader(new InputStreamReader(inputStream))
    val inputStringBuilder = reader.lines.iterator().asScala.mkString
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
    responseJson.put("headers", headerJson)
    responseJson.put("statusCode", "200")

    val parsed = Try(new JSONObject(rawOutputJson))
    val outputData = parsed.getOrElse(rawOutputJson)
    responseJson.put("body", outputData)

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

class LambdaHandlerScala[U <: ApplicationRequestScala](val core: LogicCoreScala[U], val mapper: RequestMapper[U])
  extends RequestStreamHandler {

  @throws[IOException]
  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    val urlArgs = LambdaHandlerScala.parseUrlArgs(input)
    val algoReq = mapper.buildQuery(urlArgs)
    val res = algoReq match {
      case Right(req) => core.runLogic(req)
      case Left(err) =>
        LambdaHandlerScala.outputError(400, err, output)
        return
    }
    res match {
      case ApplicationResultScala.Result(resList) =>
        val resJson = resList.map(RouteJsonOutputer.output).mkString("[", ", ", "]")
        LambdaHandlerScala.outputData(resJson, output)
      case ApplicationResultScala.Error(errList) =>
        val resJson = errList.mkString(". In addition: ")
        LambdaHandlerScala.outputError(500, resJson, output)
    }
  }
}

case class DonutLambdaHandlerScala() extends LambdaHandlerScala(DonutCore, DonutRequest)


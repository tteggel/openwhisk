/*
 * Copyright 2015-2016 IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package whisk.common

import java.io.PrintStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import akka.event.Logging.{ DebugLevel, InfoLevel, WarningLevel, ErrorLevel }
import akka.event.Logging.LogLevel
import akka.event.LoggingAdapter

trait Logging {
    /**
     * Prints a message on DEBUG level
     *
     * @param from Reference, where the method was called from.
     * @param message Message to write to the log
     */
    def debug(from: AnyRef, message: String)(implicit id: TransactionId = TransactionId.unknown) = {
        emit(DebugLevel, id, from, message)
    }

    /**
     * Prints a message on INFO level
     *
     * @param from Reference, where the method was called from.
     * @param message Message to write to the log
     */
    def info(from: AnyRef, message: String)(implicit id: TransactionId = TransactionId.unknown) = {
        emit(InfoLevel, id, from, message)
    }

    /**
     * Prints a message on WARN level
     *
     * @param from Reference, where the method was called from.
     * @param message Message to write to the log
     */
    def warn(from: AnyRef, message: String)(implicit id: TransactionId = TransactionId.unknown) = {
        emit(WarningLevel, id, from, message)
    }

    /**
     * Prints a message on ERROR level
     *
     * @param from Reference, where the method was called from.
     * @param message Message to write to the log
     */
    def error(from: AnyRef, message: String)(implicit id: TransactionId = TransactionId.unknown) = {
        emit(ErrorLevel, id, from, message)
    }

    /**
     * Prints a message to the output.
     *
     * @param loglevel The level to log on
     * @param id <code>TransactionId</code> to include in the log
     * @param from Reference, where the method was called from.
     * @param message Message to write to the log
     */
    def emit(loglevel: LogLevel, id: TransactionId, from: AnyRef, message: String)
}

/**
 * Implementaion of Logging, that uses akka logging.
 */
class AkkaLogging(loggingAdapter: LoggingAdapter) extends Logging {
    def emit(loglevel: LogLevel, id: TransactionId, from: AnyRef, message: String) = {
        val name = if (from.isInstanceOf[String]) from else Logging.getCleanSimpleClassName(from.getClass)

        val logMessage = Seq(message).collect {
            case msg if msg.nonEmpty =>
                msg.split('\n').map(_.trim).mkString(" ")
        }

        val parts = Seq(s"[$id]") ++ Seq(s"[$name]") ++ logMessage
        loggingAdapter.log(loglevel, parts.mkString(" "))
    }
}

/**
 * Implementaion of Logging, that uses the output stream.
 */
class PrintStreamLogging(outputStream: PrintStream = Console.out) extends Logging {
    def emit(loglevel: LogLevel, id: TransactionId, from: AnyRef, message: String) = {
        val now = Instant.now(Clock.systemUTC)
        val time = Emitter.timeFormat.format(now)
        val name = if (from.isInstanceOf[String]) from else Logging.getCleanSimpleClassName(from.getClass)

        val level = loglevel match {
            case DebugLevel   => "DEBUG"
            case InfoLevel    => "INFO"
            case WarningLevel => "WARN"
            case ErrorLevel   => "ERROR"
        }

        val logMessage = Seq(message).collect {
            case msg if msg.nonEmpty =>
                msg.split('\n').map(_.trim).mkString(" ")
        }

        val parts = Seq(s"[$time]", s"[$level]", s"[$id]") ++ Seq(s"[$name]") ++ logMessage
        outputStream.println(parts.mkString(" "))
    }
}

/**
 * A triple representing the timestamp relative to which the elapsed time was computed,
 * typically for a TransactionId, the elapsed time in milliseconds and a string containing
 * the given marker token.
 *
 * @param token the LogMarkerToken that should be defined in LoggingMarkers
 * @param deltaToTransactionStart the time difference between now and the start of the Transaction
 * @param deltaToMarkerStart if this is an end marker, this is the time difference to the start marker
 */
case class LogMarker(token: LogMarkerToken, deltaToTransactionStart: Long, deltaToMarkerStart: Option[Long] = None) {
    override def toString() = {
        val parts = Seq("marker", token.toString, deltaToTransactionStart) ++ deltaToMarkerStart
        "[" + parts.mkString(":") + "]"
    }
}

private object Logging {
    /**
     * Given a class object, return its simple name less the trailing dollar sign.
     */
    def getCleanSimpleClassName(clz: Class[_]) = {
        val simpleName = clz.getSimpleName
        if (simpleName.endsWith("$")) simpleName.dropRight(1)
        else simpleName
    }
}

private object Emitter {
    val timeFormat = DateTimeFormatter.
        ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").
        withZone(ZoneId.of("UTC"))
}

case class LogMarkerToken(component: String, action: String, state: String) {
    override def toString() = component + "_" + action + "_" + state
}

object LoggingMarkers {

    val start = "start"
    val finish = "finish"
    val error = "error"
    val count = "count"

    private val controller = "controller"
    private val invoker = "invoker"
    private val database = "database"
    private val activation = "activation"
    private val kafka = "kafka"
    private val loadbalancer = "loadbalancer"

    // Time of the activation in controller until it is delivered to Kafka
    val CONTROLLER_ACTIVATION = LogMarkerToken(controller, activation, start)
    val CONTROLLER_ACTIVATION_BLOCKING = LogMarkerToken(controller, "blockingActivation", start)

    // Time that is needed load balance the activation
    val CONTROLLER_LOADBALANCER = LogMarkerToken(controller, loadbalancer, start)

    // Time that is needed to produce message in kafka
    val CONTROLLER_KAFKA = LogMarkerToken(controller, kafka, start)

    // Time that is needed to execute the action
    val INVOKER_ACTIVATION_RUN = LogMarkerToken(invoker, "activationRun", start)

    // Time that is needed to init the action
    val INVOKER_ACTIVATION_INIT = LogMarkerToken(invoker, "activationInit", start)

    // Time in invoker
    val INVOKER_ACTIVATION = LogMarkerToken(invoker, activation, start)
    def INVOKER_DOCKER_CMD(cmd: String) = LogMarkerToken(invoker, s"docker.$cmd", start)
    def INVOKER_RUNC_CMD(cmd: String) = LogMarkerToken(invoker, s"runc.$cmd", start)

    val DATABASE_CACHE_HIT = LogMarkerToken(database, "cacheHit", count)
    val DATABASE_CACHE_MISS = LogMarkerToken(database, "cacheMiss", count)
    val DATABASE_SAVE = LogMarkerToken(database, "saveDocument", start)
    val DATABASE_DELETE = LogMarkerToken(database, "deleteDocument", start)
    val DATABASE_GET = LogMarkerToken(database, "getDocument", start)
    val DATABASE_QUERY = LogMarkerToken(database, "queryView", start)
    val DATABASE_ATT_GET = LogMarkerToken(database, "getDocumentAttachment", start)
    val DATABASE_ATT_SAVE = LogMarkerToken(database, "saveDocumentAttachment", start)
}

/*
 * Copyright (c) 2002-2018 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j Enterprise Edition. The included source
 * code can be redistributed and/or modified under the terms of the
 * GNU AFFERO GENERAL PUBLIC LICENSE Version 3
 * (http://www.fsf.org/licensing/licenses/agpl-3.0.html) with the
 * Commons Clause, as found in the associated LICENSE.txt file.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * Neo4j object code can be licensed independently from the source
 * under separate terms from the AGPL. Inquiries can be directed to:
 * licensing@neo4j.com
 *
 * More information is also available at:
 * https://neo4j.com/licensing/
 */
package org.neo4j.cypher.internal.runtime.parallel

import java.util.concurrent.atomic.AtomicInteger

import scala.collection.mutable.ArrayBuffer

/**
  * Tracer of a scheduler.
  */
class SpatulaTracer extends SchedulerTracer {

  private val MAGIC_NUMBER = 1024
  private val queryCounter = new AtomicInteger()

  private val dataByThread: Array[ArrayBuffer[DataPoint]] =
    (0 until MAGIC_NUMBER).map(_ => new ArrayBuffer[DataPoint]).toArray

  override def traceQuery(): QueryExecutionTracer =
    QueryTracer(queryCounter.incrementAndGet())

  case class QueryTracer(id: Int) extends QueryExecutionTracer {
    override def scheduleWorkUnit(task: Task): Unit = {}

    override def startWorkUnit(task: Task): WorkUnitEvent = {
      val startTime = currentTime()
      WorkUnit(id, Thread.currentThread().getId, startTime, task)
    }
  }

  case class WorkUnit(queryId: Int, threadId: Long, startTime: Long, task: Task) extends WorkUnitEvent {
    override def stop(): Unit = {
      val stopTime = currentTime()
      dataByThread(threadId.asInstanceOf[Int]) += DataPoint(queryId, threadId, startTime, stopTime, task)
    }
  }

  case class DataPoint(queryId: Int, threadId: Long, startTime: Long, stopTime: Long, task: Task)

  private def currentTime(): Long = System.nanoTime()

  def result(): String = {
    val t0 = dataByThread.filter(_.nonEmpty).map(_.head.startTime).min
    def t(x:Long) = (x-t0) / 1000

    val sb = new StringBuilder
    sb ++= "queryId threadId startTime(us) stopTime(us) pipeline\n"
    for (dp <- dataByThread.flatten) {
      sb ++= "  %d    %5d    %10d  %10d    %s\n"
        .format(dp.queryId, dp.threadId, t(dp.startTime), t(dp.stopTime), dp.task.toString)
    }
    sb.result()
  }
}
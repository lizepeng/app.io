import java.util.Timer

import helpers.{CanonicalNamed, Logging}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Class for Killing leaked threads in dev mode.
 *
 * @author zepeng.li@gmail.com
 */
object LeakedThreadsKiller extends CanonicalNamed with Logging {

  val ru     = scala.reflect.runtime.universe
  val mirror = ru.runtimeMirror(getClass.getClassLoader)

  /**
   * A workaround for akka bug #17729
   *
   * @see [[scala.concurrent.impl.ExecutionContextImpl]]
   */
  def killScalaGlobalExecutionContext(): Future[Unit] = Future.successful {
//    val executorM = executorMirror(scala.concurrent.ExecutionContext.global)
//    val executor = executorM.apply().asInstanceOf[scala.concurrent.forkjoin.ForkJoinPool]

//    executor.shutdown()
//    Logger.info("Kill Scala Global ExecutionContext.")
  }

  /**
   * Stop leaking thread "Time-n"
   *
   * @see [[play.api.libs.iteratee.Concurrent]]
   */
  def killTimerInConcurrent(): Future[Unit] = Future.successful {

    val moduleS = mirror.staticModule("play.api.libs.iteratee.Concurrent")
    val moduleM = mirror.reflectModule(moduleS)
    val instanceM = mirror.reflect(moduleM.instance)

    val timerS = moduleS.typeSignature.decl(ru.TermName("timer")).asMethod
    val timerM = instanceM.reflectMethod(timerS)
    val timer = timerM.apply().asInstanceOf[Timer]

    timer.cancel()
    Logger.info("Kill Timer in object Concurrent.")
  }

  /**
   * Shutdown play internal ForkJoinPool
   *
   * @see [[play.core.Execution]]
   */
  def killPlayInternalExecutionContext(): Future[Unit] = Future.successful {

    val moduleS = mirror.staticModule("play.core.Execution")
    val moduleM = mirror.reflectModule(moduleS)
    val instanceM = mirror.reflect(moduleM.instance)

    val commonS = moduleS.typeSignature.decl(ru.TermName("common")).asMethod
    val commonM = instanceM.reflectMethod(commonS)
    val common = commonM.apply()

    val executorM = executorMirror(common.asInstanceOf[ExecutionContext])
    val executor = executorM.apply().asInstanceOf[java.util.concurrent.ForkJoinPool]

    executor.shutdown()
    Logger.info("Kill Play Internal ExecutionContext.")
  }

  private def executorMirror(ec: ExecutionContext): ru.MethodMirror = {
    val classSymbol = mirror.classSymbol(Class.forName("scala.concurrent.impl.ExecutionContextImpl"))
    val instanceMirror = mirror.reflect(ec)

    val executorSymbol = classSymbol.typeSignature.decl(ru.TermName("executor")).asMethod
    instanceMirror.reflectMethod(executorSymbol)
  }
}
package org.scalajs.dom.tests.shared

import org.junit.Assert.assertEquals
import org.junit.Test
import org.scalajs.dom.QueuingStrategy
import org.scalajs.dom.ReadableStream
import org.scalajs.dom.ReadableStreamController
import org.scalajs.dom.ReadableStreamReader
import org.scalajs.dom.ReadableStreamUnderlyingSource
import org.scalajs.dom.tests.shared.AsyncTesting.AsyncResult
import org.scalajs.dom.tests.shared.AsyncTesting._
import org.scalajs.dom.tests.shared.AsyncTesting.async

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits._
import scala.util.Try

trait BrowserTests extends WebCryptoApiTests {

  def read[T](reader: ReadableStreamReader[T])(values: Seq[T]): Future[Seq[T]] = {
    reader
      .read()
      .flatMap { chunk =>
        if (chunk.done) {
          Future.successful(values)
        } else {
          read(reader)(values :+ chunk.value)
        }
      }
  }

  @Test
  final def ReadableStreamConstructionAndConsumptionTest: AsyncResult = async {
    case class Tuna(color: String)

    val expectedTunas = Seq(
        Tuna("blue"),
        Tuna("red")
    )

    val stream = ReadableStream[Tuna](
        new ReadableStreamUnderlyingSource[Tuna] {
          start = js.defined({ (controller: ReadableStreamController[Tuna]) =>
            controller.enqueue(Tuna("blue"))
            controller.enqueue(Tuna("red"))
            controller.close(): js.UndefOr[js.Promise[Unit]]
          }): js.UndefOr[js.Function1[ReadableStreamController[Tuna], js.UndefOr[js.Promise[Unit]]]]
        }
    )

    read(stream.getReader())(Seq.empty)
      .map { receivedTunas =>
        assertEquals(receivedTunas, expectedTunas)
      }
  }

  @Test
  final def ReadableStreamQueueingStrategyTest: AsyncResult = async {
    val expectedStrings = Seq(
        "short one",
        "definitely a longer one"
    )

    val stream = ReadableStream[String](
        new ReadableStreamUnderlyingSource[String] {
          start = js.defined({ (controller: ReadableStreamController[String]) =>
            controller.enqueue("short one")
            controller.enqueue("definitely a longer one")
            controller.close(): js.UndefOr[js.Promise[Unit]]
          }): js.UndefOr[js.Function1[ReadableStreamController[String], js.UndefOr[js.Promise[Unit]]]]
        },
        new QueuingStrategy[String] {
          var highWaterMark = 1
          var size: js.Function1[String, Int] = { (chunk: String) =>
            chunk.length
          }
        }
    )

    read(stream.getReader())(Seq.empty)
      .map { receivedStrings =>
        assertEquals(receivedStrings, expectedStrings)
      }
  }

  @Test
  final def ReadableStreamReaderCancelUnitTest: AsyncResult = async {
    val reasonPromise = Promise[Unit]()
    val stream = ReadableStream[Unit](
        new ReadableStreamUnderlyingSource[Unit] {
          cancel = js.defined({ (reason: Any) =>
            reasonPromise.tryComplete(Try(reason.asInstanceOf[Unit]))
            (): js.UndefOr[js.Promise[Unit]]
          }): js.UndefOr[js.Function1[Any, js.UndefOr[js.Promise[Unit]]]]
        }
    )

    for {
      _ <- stream
        .getReader()
        .cancel()
        .map(assertEquals((), _))
      _ <- reasonPromise.future.map(assertEquals((), _))
    } yield ()
  }

  @Test
  final def ReadableStreamReaderCancelPromiseTest: AsyncResult = async {
    val expectedReason = "probably a good one"

    val reasonPromise = Promise[String]()
    val stream = ReadableStream[Unit](
        new ReadableStreamUnderlyingSource[Unit] {
          cancel = js.defined({ (reason: Any) =>
            reasonPromise.tryComplete(Try(reason.asInstanceOf[String]))
            (): js.UndefOr[js.Promise[Unit]]
          }): js.UndefOr[js.Function1[Any, js.UndefOr[js.Promise[Unit]]]]
        }
    )

    for {
      _ <- stream
        .getReader()
        .cancel(expectedReason)
        .map(assertEquals((), _))
      _ <- reasonPromise.future.map(assertEquals(expectedReason, _))
    } yield ()
  }

  @Test
  final def ImageDataTest(): Unit = {
    // Test ImageDataConstructors
    // from https://developer.mozilla.org/en-US/docs/Web/API/ImageData/ImageData

    import org.scalajs.dom.{ImageData, ImageDataSettings, PredefinedColorSpace}
    import PredefinedColorSpace._

    val width: Int = 200
    val height: Int = 100

    // new ImageData(width, height)
    val imgDat1: ImageData = new ImageData(width, height)
    assertEquals(imgDat1.width, width)
    assertEquals(imgDat1.height, height)
    assertEquals(imgDat1.data.length, width * height * 4)

    // new ImageData(width, height, settings) // Firefox doesn't support this constructor.
    //  val defaultImageData: ImageData = new ImageData(width, height, new ImageDataSettings { colorSpace = `display-p3` })
    //  assertEquals(defaultImageData.width, width)
    //  assertEquals(defaultImageData.height, height)

    // new ImageData(dataArray, width)
    val imgDat2: ImageData = new ImageData(imgDat1.data, width)
    assertEquals(imgDat2.width, width)
    assertEquals(imgDat2.height, height)
    assertEquals(imgDat2.data.length, width * height * 4)

    // new ImageData(dataArray, width, height)
    val imgDat3: ImageData = new ImageData(imgDat2.data, width, height)
    assertEquals(imgDat3.width, width)
    assertEquals(imgDat3.height, height)
    assertEquals(imgDat3.data.length, width * height * 4)

    // new ImageData(dataArray, width, height, settings)
    val defaultImageData: ImageData =
      new ImageData(imgDat3.data, width, height, new ImageDataSettings { colorSpace = `display-p3` })
    assertEquals(defaultImageData.width, width)
    assertEquals(defaultImageData.height, height)
  }
}

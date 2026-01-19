// See README.md for license details.

package cachematic.store
import cachematic.datatypes.Tag

import scala.math._
import org.scalatest.flatspec.AnyFlatSpec

import chisel3._
import chiseltest.simulator.WriteVcdAnnotation
import chiseltest._

/**
  * Unit test of combinational tag match unit
  *
  * From a terminal shell use:
  * {{{
  * sbt 'testOnly cachematic.store.WordMatchTest'
  * }}}
  */
class WordMatchTest extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "WordMatch" 
  
  it should "retrieve word at the offset for the matched block" in {
    test(new WordMatch(
      numWays = 3,
      blockSize = 4,
      wordSize = 8,
    )).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      for (way <- 0 until 3) {
        for (subblock <- 0 until 4) {
          dut.io.vecWords(way)(subblock).poke((subblock + way * 3).U(8.W))
        }
      }
      dut.io.idWay.poke(2.U(2.W))
      dut.io.blockOffset.poke(1.U(2.W))

      dut.io.wordOut.expect(7.U(8.W))
    }
  }
}

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
  * sbt 'testOnly cachematic.store.TagMatchTest'
  * }}}
  */
class TagMatchTest extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "TagMatch" 
  
  it should "match queried tag from retrieved vector of tags" in {
    test(new TagMatch(
      numWays = 3,
      tagType = new Tag(numTagBits = 4)
    )).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      for (way <- 0 until 3) {
        dut.io.vecTags(way).tagBits.poke(way.U(4.W))
      }
      dut.io.tag.poke(1.U(4.W))

      dut.io.isHit.expect(true.B)
      dut.io.idWay.expect(1.U(2.W))
      dut.clock.step()

      dut.io.tag.poke(3.U(4.W))
      dut.io.isHit.expect(false.B)
    }
  }
}

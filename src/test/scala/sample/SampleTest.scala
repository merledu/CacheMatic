package cachematic.sample

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec

class SampleTest extends AnyFreeSpec with ChiselScalatestTester {
  "Sample Module" in {
    test(new Sample) { dut =>
      dut.io.in.poke(4.U)
      dut.clock.step(1)
      dut.io.out.expect(4.U)
    }
  }
}

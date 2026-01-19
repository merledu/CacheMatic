// See README.md for license details.

package cachematic.store
import cachematic.datatypes.MainMemoryAddress

import scala.math._
import org.scalatest.flatspec.AnyFlatSpec

import chisel3._
import chisel3.util._
import chiseltest.simulator.WriteVcdAnnotation
import chiseltest._

/**
  * Unit test of decoupled parallel tag/data compare unit
  *
  * From a terminal shell use:
  * {{{
  * sbt 'testOnly cachematic.store.ParallelCompareTest'
  * }}}
  *
  * TODO: implement test vectors for burst reads/writes
  */
class ParallelCompareTest extends AnyFlatSpec with ChiselScalatestTester {
  def step(dut: ParallelCompare, n: Int = 1) = {
    for (_ <- 0 until n) {
      dut.clock.step()
    }
  }

  def startup(dut: ParallelCompare) = {
    dut.in.req.valid.poke(false.B)
    dut.in.evict.valid.poke(false.B)
    dut.out.isHit.ready.poke(false.B)
    dut.out.dataOut.ready.poke(false.B)
    dut.out.isHit.valid.expect(false.B)
    dut.out.dataOut.valid.expect(false.B)
    step(dut)
  }

  behavior of "ParallelCompare"

  it should "write data to memory and wait until CPU acknowledgement" in {
    val numSets = 8
    val numWays = 3
    val blockSize = 2
    val wordSize = 8
    val numTagBits = 4
    val numSetBits = log2Ceil(numSets)
    val numBlockOffsetBits = log2Ceil(blockSize)
    val numWaysBits = log2Ceil(numWays)
    val mmAddr = new MainMemoryAddress(
      numTagBits = numTagBits,
      numSetBits = numSetBits,
      numBlockOffsetBits = numBlockOffsetBits
    )

    for (isCpuWaiting <- List(false)) {
      for (numDelayCycles <- List(6)) {
        test(new ParallelCompare(
          numSets = numSets,
          numWays = numWays,
          blockSize = blockSize,
          wordSize = wordSize,
          numDelayCycles = numDelayCycles,
          mmAddrType = mmAddr
        )).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
          
          // Startup
          startup(dut)
          step(dut, 5)

          // Initiating write operation
          dut.in.req.bits.mmAddr.tag.poke(2.U(numTagBits.W))
          dut.in.req.bits.mmAddr.setId.poke(4.U(numSetBits.W))
          dut.in.req.bits.mmAddr.blockOffset.poke(0.U(numBlockOffsetBits.W))
          dut.in.req.bits.isWrite.poke(true.B)
          for (block <- 0 until blockSize) {
            dut.in.req.bits.blockIn(block).poke(block.U(wordSize.W))
          }
          dut.in.req.valid.poke(true.B)
          dut.in.evict.valid.poke(false.B)
          dut.out.isHit.ready.poke(false.B)
          dut.out.dataOut.ready.poke(false.B)

          // Wait till it spits out cache miss, expecting eviction control
          step(dut, numDelayCycles+3)
          dut.out.isHit.valid.expect(true.B)
          dut.out.isHit.bits.expect(false.B)
          dut.in.evict.ready.expect(true.B)
          // Provide evict information
          dut.in.evict.bits.idWay.poke(2.U(numWaysBits.W))
          dut.in.evict.valid.poke(true.B)
          dut.out.isHit.ready.poke(true.B)

          // Cycle through the ready signal on `isHit` to release `ParallelCompare` into the next state
          step(dut, 1)
          dut.out.isHit.ready.poke(false.B)

          // Wait till data is written to both Stores and `ParallelCompare` indicates completion
          step(dut, numDelayCycles+2)
          dut.out.isHit.ready.poke(true.B)
          dut.out.dataOut.ready.poke(true.B)
          step(dut, 1)

          startup(dut)
          step(dut, 3)

          // Initiating write operation
          dut.in.req.bits.mmAddr.tag.poke(2.U(numTagBits.W))
          dut.in.req.bits.mmAddr.setId.poke(4.U(numSetBits.W))
          dut.in.req.bits.mmAddr.blockOffset.poke(0.U(numBlockOffsetBits.W))
          dut.in.req.bits.isWrite.poke(true.B)
          for (block <- 0 until blockSize) {
            dut.in.req.bits.blockIn(block).poke(255.U(wordSize.W))
          }
          dut.in.req.valid.poke(true.B)
          dut.in.evict.valid.poke(false.B)
          dut.out.isHit.ready.poke(false.B)
          dut.out.dataOut.ready.poke(false.B)

          // Wait till it spits out cache hit
          step(dut, numDelayCycles+2)
          // Wait till it writes to the location
          step(dut, numDelayCycles+2)
          
          // Check if operation is done
          step(dut, 1)
          dut.out.isHit.valid.expect(true.B)
          dut.out.dataOut.valid.expect(true.B)
          dut.out.isHit.bits.expect(true.B)

          // Cycle through the ready signal on `isHit` to release `ParallelCompare` into the Idle state
          dut.out.isHit.ready.poke(true.B)
          dut.out.dataOut.ready.poke(true.B)
          dut.in.req.valid.poke(false.B)
          dut.in.evict.valid.poke(false.B)
          step(dut, 1)

          // Read the same location
          dut.in.req.bits.isWrite.poke(false.B)
          dut.in.req.valid.poke(true.B)
          dut.in.evict.valid.poke(false.B)
          dut.out.isHit.ready.poke(false.B)
          dut.out.dataOut.ready.poke(false.B)

          // Wait till it spits out cache read hit
          step(dut, numDelayCycles+3)
          // Cycle through the acknowledge signal
          dut.out.isHit.valid.expect(true.B)
          dut.out.isHit.ready.poke(true.B)
          dut.out.dataOut.ready.poke(true.B)
          step(dut, 1)
          startup(dut)

          step(dut, 5)
        }
      }
    }
  }
}

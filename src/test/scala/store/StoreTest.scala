// See README.md for license details.

package cachematic.store

import scala.math._
import org.scalatest.flatspec.AnyFlatSpec

import chisel3._
import chiseltest.simulator.WriteVcdAnnotation
import chiseltest._

/**
  * Unit test of decoupled tag/data store R/W operations, with internal specified delay
  *
  * 8-set, 3-way, single-port SRAM with {6,2,1} clock cycles of latency, and blocks of 2x 8-bit words
  *
  * From a terminal shell use:
  * {{{
  * sbt 'testOnly cachematic.store.StoreTest'
  * }}}
  *
  * TODO: implement test vectors for burst reads/writes
  */
class StoreTest extends AnyFlatSpec with ChiselScalatestTester {
  def step(dut: Store[_ <: Data], n: Int = 1) = {
    for (_ <- 0 until n) {
      dut.clock.step()
    }
  }

  def startup(dut: Store[_ <: Data]) = {
    dut.in.bits.isWrite.poke(false.B)
    dut.in.valid.poke(false.B)
    dut.out.ready.poke(false.B)
    dut.out.valid.expect(false.B)
    step(dut)
  }

  def checkIdle(dut: Store[_ <: Data]) = {
    dut.in.ready.expect(true.B)
    dut.out.valid.expect(false.B)
  }

  def initRead(dut: Store[_ <: Data], addr: Int) = {
    dut.in.bits.addr.poke(addr.U)
    dut.in.bits.isWrite.poke(false.B)
    dut.in.valid.poke(true.B)
    dut.out.ready.poke(false.B)
  }

  def initWrite(dut: Store[_ <: Data], addr: Int) = {
    dut.in.bits.addr.poke(addr.U)
    dut.in.bits.isWrite.poke(true.B)
    dut.in.valid.poke(true.B)
    dut.out.ready.poke(false.B)
  }

  def checkOutputValid(dut: Store[_ <: Data]) = {
    dut.in.ready.expect(false.B)
    dut.out.valid.expect(true.B)
  }

  def checkDelayedOutput(dut: Store[_ <: Data], delay: Int) = {
    for (cycles <- 0 until delay) {
      step(dut)
      dut.out.valid.expect(false.B)
    }
    step(dut)
    checkOutputValid(dut)
  }

  def acknowledgeOutput(dut: Store[_ <: Data]) = {
    dut.in.valid.poke(false.B)
    dut.out.ready.poke(true.B)
    step(dut)
  }

  behavior of "Store"

  it should "read data from memory and latch it until CPU is ready" in {
    val depth = 8
    val numWays = 3
    val blockSize = 2
    val wordSize = 8
    val set = 5
    for (isCpuWaiting <- List(false, true)) {
      for (numDelayCycles <- List(6, 2, 1)) {
        test(new Store(
          depth = depth,
          numDelayCycles = numDelayCycles,
          numWays = numWays,
          dataType = Vec(blockSize, UInt(wordSize.W))
        )).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

          // Startup
          startup(dut)

          // Should be in Idle
          checkIdle(dut)
          step(dut)

          // Initiate read operation
          initRead(dut, set)

          // Should become valid after internal delay, marked by `out.valid` stalls the CPU
          checkDelayedOutput(dut, numDelayCycles)

          // Wait in Done state with latched data until CPU is ready
          if (!isCpuWaiting) {
            step(dut, 3)
            checkOutputValid(dut)
          }

          // Acknowledge output reading
          acknowledgeOutput(dut)

          // Should be in Idle again
          checkIdle(dut)
        }
      }
    }
  }

  it should "write data to full cache set and wait until CPU acknowledgement" in {
    val depth = 8
    val numWays = 3
    val blockSize = 2
    val wordSize = 8
    val set = 5
    for (isCpuWaiting <- List(false, true)) {
      for (numDelayCycles <- List(6, 2, 1)) {
        test(new Store(
          depth = depth,
          numDelayCycles = numDelayCycles,
          numWays = numWays,
          dataType = Vec(blockSize, UInt(wordSize.W))
        )).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

          // Startup
          startup(dut)

          // Should be in Idle
          checkIdle(dut)
          step(dut)

          // Initiating write operation to all ways in the set
          for (way <- 0 until numWays) {
            for (block <- 0 until blockSize) {
              dut.in.bits.dataIn(way)(block).poke(way.U(wordSize.W))
            }
            dut.in.bits.mask(way).poke(true.B)
          }
          initWrite(dut, set)

          // Should become valid after internal delay, marked by `out.valid` stalls the CPU
          checkDelayedOutput(dut, numDelayCycles)

          // Waits in Done state with latched data until CPU is ready
          if (!isCpuWaiting) {
            step(dut, 3)
            checkOutputValid(dut)
          }

          // Acknowledge output reading
          acknowledgeOutput(dut)

          // Should be in Idle again
          checkIdle(dut)

          // Initiating read operation
          initRead(dut, set)

          // Should become valid after internal delay, marked by `out.valid` stalls the CPU
          checkDelayedOutput(dut, numDelayCycles)

          // Wait in Done state with latched data until CPU is ready
          if (!isCpuWaiting) {
            step(dut, 3)
            checkOutputValid(dut)
          }

          // Acknowledge output reading
          acknowledgeOutput(dut)

          // Should be in Idle again
          checkIdle(dut)
          step(dut, 3)
        }
      }
    }
  }

  it should "write data to a single way in a cache set and wait until CPU acknowledgement" in {
    val depth = 8
    val numWays = 3
    val blockSize = 2
    val wordSize = 8
    val mask = 2
    val set = 3
    for (isCpuWaiting <- List(false, true)) {
      for (numDelayCycles <- List(6, 2, 1)) {
        test(new Store(
          depth = depth,
          numDelayCycles = numDelayCycles,
          numWays = numWays,
          dataType = Vec(blockSize, UInt(wordSize.W))
        )).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

          // Startup
          startup(dut)

          // Should be in Idle
          checkIdle(dut)
          step(dut)

          // Initiating write operation to one way in the set (but provide data to the complete bus)
          for (way <- 0 until numWays) {
            for (block <- 0 until blockSize) {
              dut.in.bits.dataIn(way)(block).poke(way.U(wordSize.W))
            }
            dut.in.bits.mask(way).poke(false.B)
          }
          dut.in.bits.mask(mask).poke(true.B)

          initWrite(dut, set)

          // Should become valid after internal delay, marked by `out.valid` stalls the CPU
          checkDelayedOutput(dut, numDelayCycles)

          // Waits in Done state with latched data until CPU is ready
          if (!isCpuWaiting) {
            step(dut, 3)
            checkOutputValid(dut)
          }

          // Acknowledge output reading
          acknowledgeOutput(dut)

          // Should be in Idle again
          checkIdle(dut)

          // Initiating read operation
          initRead(dut, set)

          // Should become valid after internal delay, marked by `out.valid` stalls the CPU
          checkDelayedOutput(dut, numDelayCycles)

          // Wait in Done state with latched data until CPU is ready
          if (!isCpuWaiting) {
            step(dut, 3)
            checkOutputValid(dut)
          }

          // Acknowledge output reading
          for (block <- 0 until blockSize) {
            dut.out.bits.dataOut(mask)(block).expect(mask.U(wordSize.W))
          }
          acknowledgeOutput(dut)

          // Should be in Idle again
          checkIdle(dut)
          step(dut, 3)

          // Initiating write operation to one way in the set (but provide data to the complete bus)
          for (way <- 0 until numWays) {
            for (block <- 0 until blockSize) {
              dut.in.bits.dataIn(way)(block).poke(1.U(wordSize.W))
            }
            dut.in.bits.mask(way).poke(false.B)
          }
          dut.in.bits.mask(mask).poke(true.B)

          initWrite(dut, 1)

          // Should become valid after internal delay, marked by `out.valid` stalls the CPU
          checkDelayedOutput(dut, numDelayCycles)

          // Waits in Done state with latched data until CPU is ready
          if (!isCpuWaiting) {
            step(dut, 3)
            checkOutputValid(dut)
          }

          // Acknowledge output reading
          acknowledgeOutput(dut)

          // Should be in Idle again
          checkIdle(dut)

          // Initiating read operation
          initRead(dut, 1)

          // Should become valid after internal delay, marked by `out.valid` stalls the CPU
          checkDelayedOutput(dut, numDelayCycles)

          // Wait in Done state with latched data until CPU is ready
          if (!isCpuWaiting) {
            step(dut, 3)
            checkOutputValid(dut)
          }

          // Acknowledge output reading
          for (block <- 0 until blockSize) {
            dut.out.bits.dataOut(mask)(block).expect(1.U(wordSize.W))
          }
          acknowledgeOutput(dut)

          // Should be in Idle again
          checkIdle(dut)
        }
      }
    }
  }
}

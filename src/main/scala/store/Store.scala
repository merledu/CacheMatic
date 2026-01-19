// See README.md for license details.

package cachematic.store

import chisel3._
import chisel3.util._

import cachematic.util.scala.isPowerOfTwo

object StoreState extends ChiselEnum {
  val sIdle, sAccess, sValid, sDone = Value
}

/**
  * Decoupled SRAM interfacing module with parametrizeable delay to match memory technology spec
  *   TODO: Replace the SyncReadMem module with actual parametrized black-box Verilog OpenRAM module, once defined
  *   TODO: Add the functionality to mask inputs to write individual byte or word to memory
  *   TODO: Enable burst/coalesced transactions (R/W without Idle and Hold state in between if new data is available)
  *   TODO: Adapt to wrap and match the functionality of OpenRAM
  */
class Store[T <: Data](depth: Int, numDelayCycles: Int, numWays: Int, private val dataType: T) extends Module {
  require(depth > 0,          "Depth of memory must be positive")
  require(numWays > 0,        "Number of ways must be a positive number")
  require(numDelayCycles > 0, "SRAM access delay spec must be a positive number")

  import StoreState._

  val in = IO(Flipped(Decoupled(new Bundle {
    val addr = UInt(log2Ceil(depth).W)
    val mask = Vec(numWays, Bool())
    val isWrite = Bool()
    val dataIn = Vec(numWays, dataType)
  })))
  val out = IO(Decoupled(new Bundle {
    val dataOut = Vec(numWays, dataType)
  }))

/**
  * Tag/data store, single-port R/W
  *   NOTE: Reads the whole set. Emulates function of SRAM, not construction -> replace with actual SRAM technology
  *     [OpenRAM]: https://escholarship.org/content/qt8x19c778/qt8x19c778_noSplash_b2b3fbbb57f1269f86d0de77865b0691.pdf
  */
  val store = SyncReadMem(depth, Vec(numWays, dataType))

/**
  * FSM (Moore)
  */
  val stateReg = RegInit(sIdle)
  val numDelayBits = if (numDelayCycles > 1) log2Ceil(numDelayCycles) else 1
  val delayCounterReg = RegInit(0.U(numDelayBits.W))
  // Request inputs are latched for async memory access
  val en = WireDefault(false.B)
  val addrReg = RegInit(0.U(log2Ceil(depth).W))
  val maskReg = Reg(Vec(numWays, Bool()))
  val isWriteReg = RegInit(false.B)
  val dataInReg = Reg(Vec(numWays, dataType))
  // Memory output is latched by the memory module's output registers
  // TODO: latch memory read data into the register until upstream acknowledges read
  // val dataOutReg = Reg(Vec(numWays, dataType))

  // State transitions
  switch (stateReg) {
    is (sIdle) {
      when (in.valid) {
        stateReg := sAccess
      }
    }
    is (sAccess) {
      when ((delayCounterReg === (numDelayCycles-1).U(numDelayBits.W)) && ~isWriteReg) { // read operation -> latch memory output
        stateReg := sValid
      } .elsewhen ((delayCounterReg === (numDelayCycles-1).U(numDelayBits.W)) && isWriteReg) { // write operation
        stateReg := sDone
      }
    }
    is (sValid) {
      when (out.ready) { // CPU ready to receive dataOut immediately
        stateReg := sIdle
      } .otherwise { // CPU reads latched dataOut when CPU is ready
        stateReg := sDone
      }
    }
    is (sDone) { // latched memory output
      when (out.ready) {
        stateReg := sIdle
      }
    }
  }

  // Output logic
  in.ready := WireDefault(false.B)
  out.valid := WireDefault(false.B)
  out.bits.dataOut := DontCare

  // Returns K-way elements
  when (en) {
    val rwPort = store(addrReg)
    when (isWriteReg) {
      for (way <- 0 until numWays) {
        when (maskReg(way)) {
          rwPort(way) := dataInReg(way)
        }
      }
    } .otherwise {
      out.bits.dataOut := rwPort
    }
  }

  switch (stateReg) {
    is (sIdle) {
      in.ready := true.B
      delayCounterReg := 0.U
      addrReg := in.bits.addr
      maskReg := in.bits.mask
      isWriteReg := in.bits.isWrite
      dataInReg := in.bits.dataIn
    }
    is (sAccess) {
      en := true.B
      delayCounterReg := delayCounterReg + 1.U
    }
    is (sValid) {
      out.valid := true.B
      // out.bits.dataOut := dataOutReg
    }
    is (sDone) {
      out.valid := true.B
      // out.bits.dataOut := dataOutReg
      when (out.ready) {
        delayCounterReg := 0.U
      }
    }
  }
}

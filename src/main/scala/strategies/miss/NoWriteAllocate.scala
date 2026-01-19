// See README.md for license details.

package cachematic.strategies.miss

import chisel3._
import chisel3.util.Decoupled

/**
  * 
  */
class NoWriteAllocate extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(4.W))
    val out = Output(UInt(4.W))
  })
  val reg = RegInit(0.U)

  reg := io.in
  io.out := reg
}

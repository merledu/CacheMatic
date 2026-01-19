// See README.md for license details.

package cachematic.policies.replacement

import chisel3._
import chisel3.util.Decoupled

/**
  * 
  */
class Random(width: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(4.W))
    val out = Output(UInt(4.W))
  })
  val reg = RegInit(0.U)

  reg := io.in
  io.out := reg
}

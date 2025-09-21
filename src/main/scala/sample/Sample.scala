package cachematic.sample

import chisel3._

class SampleBundle extends Bundle {
  val in = Input(UInt(4.W))
  val out = Output(UInt(4.W))
}

class Sample extends Module {
  val io = IO(new SampleBundle)
  val reg = RegInit(0.U)

  reg := io.in
  io.out := reg
}

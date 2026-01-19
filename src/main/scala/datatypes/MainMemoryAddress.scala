// See README.md for license details.

package cachematic.datatypes

import chisel3._
import chisel3.util._

class MainMemoryAddress(val numTagBits: Int, val numSetBits: Int, val numBlockOffsetBits: Int) extends Bundle {
  val tag           = UInt(numTagBits.W)
  val setId         = UInt(numSetBits.W)
  val blockOffset   = UInt(numBlockOffsetBits.W)
}

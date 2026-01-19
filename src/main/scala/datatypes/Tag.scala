// See README.md for license details.

package cachematic.datatypes

import chisel3._
import chisel3.util._

// TODO: extend with desired status bits (valid, dirty, etc.)
// NOTE: Does not include atm status bits (e.g. valid/dirty) -> could contain per-word in a block, not just per-block
class Tag(numTagBits: Int) extends Bundle {
  val tagBits     = UInt(numTagBits.W)
  // val statusBits  =
}

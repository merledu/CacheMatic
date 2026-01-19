// See README.md for license details.

package cachematic.datatypes

import chisel3._
import chisel3.util._

// TODO: extend with desired busses and connections
class CacheInterface[T <: Data](private val mmAddr: T) extends Bundle {
  val addr    = mmAddr.cloneType
  val isWrite = Bool()
}

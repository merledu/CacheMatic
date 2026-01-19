// See README.md for license details.
package cachematic.util.chisel

import chisel3._
import chisel3.util._

object Comp {
  def apply[T <: Data](a: T, b: T): Bool = a === b
}
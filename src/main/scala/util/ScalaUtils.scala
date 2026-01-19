// See README.md for license details.
package cachematic.util.scala

import chisel3._
import chisel3.util._

object isPowerOfTwo {
  def apply(n: Int): Boolean = ((n > 0) && ((n & (n - 1)) == 0))
}

// See README.md for license details.

package cachematic.store

import chisel3._
import chisel3.util._

import cachematic.util.chisel.Comp
import cachematic.datatypes.Tag

/**
  * Tag compare module
  */
class TagMatch(numWays: Int, private val tagType: Tag) extends Module {
  require(numWays > 0, "Number of ways must be a positive number")

  val widthIdWay = log2Ceil(numWays)

  val io = IO(new Bundle {
    val tag = Input(tagType.tagBits.cloneType)
    val vecTags = Input(Vec(numWays, tagType.cloneType))
    val isHit = Output(Bool())
    val idWay = Output(UInt(widthIdWay.W))
  })

  // K-way parallel tag comparators
  val vecTagComparators = VecInit(Seq.tabulate(numWays) {x => Comp(io.tag, io.vecTags(x).tagBits)})
  io.isHit := vecTagComparators.asUInt.orR
  
  // Encode position into index for data store multiplexer
  io.idWay := MuxCase(DontCare, Array.tabulate(numWays) {x => (vecTagComparators(x), x.U(widthIdWay.W))})
}

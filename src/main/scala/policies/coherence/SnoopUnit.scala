// See README.md for license details.

package cachematic.policies.coherence

import chisel3._
import chisel3.util._

/**
 * Snoop Unit for implementing the Write-Invalidate Protocol.
 * This unit uses combinational logic to determine if an external write
 * hits an existing cache line, and if so, sets its valid bit to false.
 * * @param lineSizeBytes The size of a cache line in bytes (e.g., 64).
 * * @param numLines The total number of lines (e.g., 128 lines)
 */
class SnoopUnit(lineSizeBytes: Int, numLines: Int) extends Module {
  
  // Calculate necessary parameters internally
  val numLinesLog2 = log2Ceil(numLines)
  val nOffsetBits = log2Ceil(lineSizeBytes)
  val nTagBits = 32 - numLinesLog2 - nOffsetBits

  val io = IO(new Bundle {
    // External snooping bus interface
    val snoop = Flipped(ValidIO(UInt(32.W)))

    // Internal cache metadata interface (Inputs)
    val tags_in = Input(Vec(numLines, UInt(nTagBits.W)))
    val valid_in = Input(Vec(numLines, Bool()))
    
    // Output: The calculated next state of the valid bits.
    val next_valid_out = Output(Vec(numLines, Bool()))
  })

  // Assume no invalidation, pass through the current valid state.
  val next_valid = Wire(Vec(numLines, Bool()))
  next_valid := io.valid_in

  // Snoop Address Decoding (using locally calculated bit widths)
  val snoop_addr = io.snoop.bits
  val snoop_tag = snoop_addr(31, 32 - nTagBits)
  val snoop_index = snoop_addr(31 - nTagBits, nOffsetBits)

  // Snooping Logic: Operates only when a snoop request is valid.
  when(io.snoop.valid) {
    // Check for a tag match on a valid line.
    val snoop_hit = io.valid_in(snoop_index) && (io.tags_in(snoop_index) === snoop_tag)
    
    // If we have a hit, we must invalidate the specific cache line.
    when(snoop_hit) {
      next_valid(snoop_index) := false.B
    }
  }
  
  io.next_valid_out := next_valid
}

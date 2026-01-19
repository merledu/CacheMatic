// See README.md for license details.

package cachematic.store

import chisel3._
import chisel3.util._

import cachematic.util.scala.isPowerOfTwo 

/**
  * Word extract module
  */
class WordMatch(numWays: Int, blockSize: Int, wordSize: Int) extends Module {
  require(numWays > 0,              "Number of ways must be a positive number")
  require(isPowerOfTwo(blockSize),  "Cache block/line size must be positive and a power of 2")
  require(isPowerOfTwo(wordSize),   "Word size must be positive and a power of 2")

  val widthIdWay = log2Ceil(numWays)
  val widthBlockOffset = log2Ceil(blockSize)

  val io = IO(new Bundle {
    val vecWords = Input(Vec(numWays, Vec(blockSize, UInt(wordSize.W))))
    val idWay = Input(UInt(widthIdWay.W))
    val blockOffset = Input(UInt(widthBlockOffset.W))
    val wordOut = Output(UInt(wordSize.W))
  })

  // Matched block
  val block = io.vecWords(io.idWay)
  
  // Get block at the offset indicated by offset bits
  io.wordOut := block(io.blockOffset)
}

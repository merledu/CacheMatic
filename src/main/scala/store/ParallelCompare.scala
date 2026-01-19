// See README.md for license details.

package cachematic.store

import chisel3._
import chisel3.util._

import cachematic.util.scala.isPowerOfTwo
import cachematic.store.Store
import cachematic.datatypes.{Tag, MainMemoryAddress}

object ParallelCompareState extends ChiselEnum {
  val sIdle, sRead, sWrite, sWriteHit, sWriteMiss, sEvict, sDone = Value
}

/**
  * Parallel tag and data store fetch module (meant for small caches)
  *   NOTE: Upstream module must not revoke data after asserting any signals, until result is produced
  *   NOTE: Upstream module decides what to do based on the result of the tag comparison
  *   NOTE: For now writes a full cache block and not individual words
  */
class ParallelCompare(numSets: Int, numWays: Int, blockSize: Int, wordSize: Int, numDelayCycles: Int, private val mmAddrType: MainMemoryAddress) extends Module {
  require(isPowerOfTwo(numSets),    "Number of sets must be positive and a power of 2")
  require(numWays > 0,              "Number of ways must be a positive number")
  require(isPowerOfTwo(blockSize),  "Cache block/line size must be positive and a power of 2")
  require(isPowerOfTwo(wordSize),   "Word size must be positive and a power of 2")
  require(numDelayCycles > 0,       "SRAM access delay spec must be a positive number (i.e. 1 is data available on the next clock cycle)")

  // TODO?: Datatypes for convenience, move upstream and pass as type
  val tagStoreEntry = new Tag(mmAddrType.numTagBits)
  val dataStoreEntry = Vec(blockSize, UInt(wordSize.W))

  import ParallelCompareState._

  val widthIdMatchedTag = log2Ceil(numWays)

  val in = IO(new Bundle {
    val req = Flipped(Decoupled(new Bundle {
      val mmAddr = mmAddrType.cloneType
      val isWrite = Bool()
      val blockIn = dataStoreEntry.cloneType // 1 out of N way blocks in the set
    }))
    val evict = Flipped(Decoupled(new Bundle {
      val idWay = UInt(widthIdMatchedTag.W) // used in case of read/write miss by the replacement policy
    }))
  })

  val out = IO(new Bundle {
    val isHit = Decoupled(Bool()) // flag whether requested address in cache
    val dataOut = Decoupled(UInt(wordSize.W)) // contents of requested address
  })

  // Submodules
  // NOTE: for parallel compare, we assume Tag and Data store access times are the same (or worst case of the two - data store)
  val tagStore = Module(new Store(numSets, numDelayCycles, numWays, tagStoreEntry))
  val tagMatch = Module(new TagMatch(numWays, tagStoreEntry))
  val dataStore = Module(new Store(numSets, numDelayCycles, numWays, dataStoreEntry))
  val wordMatch = Module(new WordMatch(numWays, blockSize, wordSize))

/**
  * FSM Controls enable signals to both stores for retreiving cache entries
  * NOTE: will manage valid/ready signals on the decoupled up- and downstream interfaces to coordinate logic
  */
  val stateReg = RegInit(sIdle)

  // State transitions
  switch (stateReg) {
    is (sIdle) { // waits for read/write transactions from upstream
      when (in.req.valid && ~in.evict.valid) {
        when (in.req.bits.isWrite) {
          stateReg := sWrite
        } .otherwise {
          stateReg := sRead
        }
      } .elsewhen (in.req.valid && in.evict.valid) {
        stateReg := sEvict
      }
    }
    is (sRead) { // checks if to-be-read block in cache
      when (tagStore.out.valid && dataStore.out.valid) {
        stateReg := sDone
      }
    }
    is (sWrite) { // checks if to-be-written block in cache
      when (tagStore.out.valid) {
        when (tagMatch.io.isHit) {
          stateReg := sWriteHit
        } .otherwise {
          stateReg := sWriteMiss
        }
      }
    }
    is (sWriteHit) { // to-be-written block already in cache, updates contents
      when (dataStore.out.valid) {
        stateReg := sDone
      }
    }
    is (sWriteMiss) { // to-be-written block not in cache, waits until upstream provides evict index within the set
      when (in.evict.valid) {
        stateReg := sEvict
      }
    }
    is (sEvict) { // tag- and data- store evict cache block in place of the new one on the `io.req` interface
      when (tagStore.out.valid && dataStore.out.valid) {
        stateReg := sDone
      }
    }
    is (sDone) { // upstream acknowledged reading of the output
      when (out.isHit.ready && out.dataOut.ready) {
        stateReg := sIdle
      }
    }
  }

/**
  * State logic
  * NOTE: control of Decoupled IO will be managed by present Compare module
  */
  // Set default signals
  in.req.ready := WireDefault(false.B)
  in.evict.ready := WireDefault(false.B)
  out.isHit.valid := WireDefault(false.B)
  out.dataOut.valid := WireDefault(false.B)
  tagStore.out.ready := WireDefault(false.B)
  dataStore.out.ready := WireDefault(false.B)
  tagStore.in.valid := WireDefault(false.B)
  dataStore.in.valid := WireDefault(false.B)
  val idHitWayReg = Reg(UInt(widthIdMatchedTag.W))

  // Make permanent connections from Store submodules to upstream caller module
  tagStore.in.bits.addr := in.req.bits.mmAddr.setId
  tagStore.in.bits.isWrite := WireDefault(false.B)
  tagStore.in.bits.dataIn := DontCare
  tagStore.in.bits.mask := DontCare

  dataStore.in.bits.addr := in.req.bits.mmAddr.setId
  dataStore.in.bits.isWrite := WireDefault(false.B)
  dataStore.in.bits.dataIn := DontCare
  dataStore.in.bits.mask := DontCare

  // Permanently connect Tag store output to Tag matching circuit
  tagMatch.io.tag := in.req.bits.mmAddr.tag
  tagMatch.io.vecTags := tagStore.out.bits.dataOut

  // Permanently connect Word matching circuit to Data store output and Tag matching circuit 
  wordMatch.io.idWay := tagMatch.io.idWay
  wordMatch.io.vecWords := dataStore.out.bits.dataOut
  wordMatch.io.blockOffset := in.req.bits.mmAddr.blockOffset

  // Permanently connect downstream outputs to upstream module
  // NOTE: validity of data will be indicated by valid/ready signals managed by present module
  out.isHit.bits := tagMatch.io.isHit
  out.dataOut.bits := wordMatch.io.wordOut

  switch (stateReg) {
    is (sIdle) { // waiting for new upstream request, keeps downstream tag/data store modules in Idle
      in.req.ready := true.B
      in.evict.ready := true.B
    }
    is (sRead) { // waits until tag and data store produce a read result
      tagStore.in.valid := true.B
      dataStore.in.valid := true.B
    }
    is (sWrite) { // latch ID of the matched block in the set if already in cache
      tagStore.in.valid := true.B
      when (tagStore.out.valid) {
        when (tagMatch.io.isHit) {
          idHitWayReg := tagMatch.io.idWay
        }
      }
    }
    is (sWriteHit) { // update contents of the hit block at the latched ID
      dataStore.in.bits.dataIn(in.evict.bits.idWay) := in.req.bits.blockIn
      val mask = VecInit((1.U(numWays.W) << idHitWayReg)(numWays-1, 0).asBools)
      dataStore.in.bits.mask := mask // convert ID of matched way in the set to a OHE for use as a mask
      dataStore.in.valid := true.B
      dataStore.in.bits.isWrite := true.B
    }
    is (sWriteMiss) { // cycle through valid/ready on the `out` interface to get eviction ID from the upstream module
      out.isHit.valid := true.B
      in.evict.ready := true.B
      tagStore.out.ready := true.B
      dataStore.out.ready := true.B
    }
    is (sEvict) { // use the block eviction ID to replace the tag and data store entry at that index
      tagStore.in.bits.dataIn(in.evict.bits.idWay).tagBits := in.req.bits.mmAddr.tag
      dataStore.in.bits.dataIn(in.evict.bits.idWay) := in.req.bits.blockIn
      val mask = VecInit((1.U(numWays.W) << in.evict.bits.idWay)(numWays-1, 0).asBools)
      tagStore.in.bits.mask := mask
      dataStore.in.bits.mask := mask
      tagStore.in.valid := true.B
      dataStore.in.valid := true.B
      tagStore.in.bits.isWrite := true.B
      dataStore.in.bits.isWrite := true.B
    }
    is (sDone) {
      out.isHit.valid := true.B
      out.dataOut.valid := true.B
      when (out.isHit.ready) { // cycle Store FSMs to release the output and transition to Idle
        tagStore.out.ready := true.B
        dataStore.out.ready := true.B
      }
    }
  }
}

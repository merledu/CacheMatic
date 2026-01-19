// See README.md for license details.

package cachematic.cache

import chisel3._
import chisel3.util._

/**
 * Defines the type of cache to be instantiated.
 */
object CacheType extends ChiselEnum {
  val Instruction, Data = Value
}

class DirectMapped(
  val cacheType: CacheType.Type, 
  val cacheSizeBytes: Int, 
  val blockSizeBytes: Int
) extends Module {
  val addrWidth = 32
  val dataWidth = 32

  val io = IO(new Bundle {
    // CPU-facing interface
    // Flipped(Decoupled(...)) creates a ready/valid interface for inputs
    val cpuReq = Flipped(Decoupled(new MemRequestIO))
    // Decoupled(...) creates a ready/valid interface for outputs
    val cpuRsp = Decoupled(new MemResponseIO)
    // Memory-facing interface
    val memReq = Decoupled(new MemRequestIO)
    val memRsp = Flipped(Decoupled(new MemResponseIO))
  })

  val numLines   = cacheSizeBytes / blockSizeBytes
  val wordsPerBlock = blockSizeBytes / (dataWidth / 8)
  val indexBits  = log2Ceil(numLines)
  val offsetBits = log2Ceil(blockSizeBytes)
  val tagsBits   = addrWidth - indexBits - offsetBits

  // --- State Machine ---
  val s_IDLE :: s_READ_HIT :: s_WRITE_HIT :: s_MEM_REQ_START :: s_MEM_RESP_WAIT :: s_MEM_REQ_NEXT :: Nil = Enum(6)
  val state = RegInit(s_IDLE)

  // CPU request decoding
  val cpuReqAddr = io.cpuReq.bits.addrRequest
  val cpuReqTag   = cpuReqAddr(addrWidth - 1, offsetBits + indexBits)
  val cpuReqIndex = cpuReqAddr(offsetBits + indexBits - 1, offsetBits)
  val word_offset = cpuReqAddr(offsetBits - 1, log2Ceil(dataWidth / 8))
  val cacheLineBaseAddr = Cat(cpuReqAddr(addrWidth - 1, offsetBits), 0.U(offsetBits.W))

  val validArray = RegInit(VecInit(Seq.fill(numLines)(false.B)))
  val tagArray   = RegInit(VecInit(Seq.fill(numLines)(0.U(tagsBits.W))))
  // val dataArray  = RegInit(VecInit(Seq.fill(numLines)(0.U(dataWidth.W))))
  val dataArray  = Reg(Vec(numLines, Vec(wordsPerBlock, UInt(dataWidth.W))))
  
  // --- Regs for handling misses ---
  val missIsWrite = RegInit(false.B)
  val missAddr    = RegInit(0.U(addrWidth.W))
  val missWData   = RegInit(0.U(dataWidth.W))

  // --- Regs for handling multi-word read miss responses ---
  // counter for words (in a block) received from memory
  val missWordCounter = RegInit(0.U((log2Ceil(wordsPerBlock)+1).W))
  val missIndex = missAddr(offsetBits + indexBits - 1, offsetBits)
  val missTag   = missAddr(addrWidth - 1, offsetBits + indexBits)
  val missWordOffset = missAddr(offsetBits - 1, log2Ceil(dataWidth / 8))
  
  // --- Regs for registered outputs
  // val cpuReqReadyReg        = RegInit(true.B)
  // val memReadRespReadyReg   = RegInit(true.B)
  val cpuRspValidReg        = RegInit(false.B)
  val cpuRspDataReg         = RegInit(0.U(dataWidth.W))
  val memReqValidReg    = RegInit(false.B)
  val memReqAddrReg     = RegInit(0.U(addrWidth.W))
  val memReqDataReg    = RegInit(0.U(dataWidth.W))
  val memReqIsWrite        = RegInit(false.B)

  val isHit  = validArray(cpuReqIndex) && (tagArray(cpuReqIndex) === cpuReqTag)
  val isWrite = io.cpuReq.bits.isWrite && (cacheType == CacheType.Data).B

  // --- Default signals assignments ---
  // io.cpuReq.ready := cpuReqReadyReg
  io.cpuReq.ready := (state === s_IDLE)
  // io.memRsp.ready := memReadRespReadyReg
  io.memRsp.ready := true.B  // always ready to accept memory responses

  io.cpuRsp.valid := cpuRspValidReg
  io.cpuRsp.bits.dataResponse := cpuRspDataReg
  io.memReq.valid := memReqValidReg
  io.memReq.bits.addrRequest := memReqAddrReg
  io.memReq.bits.dataRequest := memReqDataReg
  io.memReq.bits.isWrite := memReqIsWrite
  io.memReq.bits.activeByteLane := "b1111".U // default receive all bytes

  // Default regs values
  // cpuReqReadyReg := true.B
  // memReadRespReadyReg := true.B
  cpuRspValidReg := false.B
  cpuRspDataReg  := 0.U
  memReqValidReg := false.B
  memReqAddrReg  := 0.U
  memReqDataReg  := 0.U
  memReqIsWrite  := false.B

  switch(state) {
    is(s_IDLE) {
      when(io.cpuReq.valid && io.cpuReq.ready) {
        when (isHit) {
          when (isWrite) {
            state := s_WRITE_HIT
            // Write Hit:
            // update cache line
            dataArray(cpuReqIndex)(word_offset) := io.cpuReq.bits.dataRequest
            // write through to memory
            memReqValidReg := true.B
            memReqAddrReg := cpuReqAddr
            memReqDataReg := io.cpuReq.bits.dataRequest
            memReqIsWrite := true.B
          } .otherwise {
            state := s_READ_HIT
            // Read Hit:
            // return data immediately
            cpuRspValidReg := true.B
            cpuRspDataReg := dataArray(cpuReqIndex)(word_offset)
          }
        } .otherwise {
          // Write miss (write-allocate policy) and Read miss handled the same way
          // Send read request to memory, starting from first word of the cache line
          state := s_MEM_REQ_START
          // cpuReqReadyReg := false.B // stall CPU until miss is handled
          memReqValidReg := true.B
          memReqAddrReg := cacheLineBaseAddr
          memReqIsWrite := false.B
          missWordCounter := 0.U // reset word counter
          // save miss request info for later use
          missIsWrite := isWrite
          missAddr := cpuReqAddr
          missWData := io.cpuReq.bits.dataRequest
        }
      }
    }
    is(s_READ_HIT) {
      when (io.cpuRsp.valid && io.cpuRsp.ready) {
        state := s_IDLE
      }
    }
    is(s_WRITE_HIT) {
      when (io.memReq.valid && io.memReq.ready) {
        state := s_IDLE
      }
    }
    is(s_MEM_REQ_START) {
      // send read request from cache to memory
      when(io.memReq.valid && io.memReq.ready) {
        state := s_MEM_RESP_WAIT
        memReqValidReg := false.B // deassert read request valid
        // memReadRespReadyReg := true.B // ready to receive memory response
      }

    }
    // wait for memory response
    is(s_MEM_RESP_WAIT) {
      // wait for memory response with the requested cache line
      when(io.memRsp.valid && io.memRsp.ready) {
        // store the received word into the cache line
        dataArray(missIndex)(missWordCounter) := io.memRsp.bits.dataResponse

        when(missWordCounter === (wordsPerBlock-1).U) {
          // last word received
          // update cpuReqTag and valid bit of the cache line
          validArray(missIndex) := true.B
          tagArray(missIndex)   := missTag

          // handle specific read miss/ write miss
          when(missIsWrite) {
            // Write miss: 
            // update cache line with the missed write data
            dataArray(missIndex)(missWordOffset) := missWData
            // write through to memory
            memReqValidReg := true.B
            memReqAddrReg := missAddr
            memReqDataReg := missWData
            memReqIsWrite := true.B
          } .otherwise {
            // Read miss:
            // return the requested data to CPU
            val cpuRspData = Mux(missWordOffset === (wordsPerBlock - 1).U,
                     io.memRsp.bits.dataResponse,
                     dataArray(missIndex)(missWordOffset))
            cpuRspValidReg := true.B
            cpuRspDataReg := cpuRspData
          }
          state := s_IDLE
        } .otherwise {
          // not the last word yet, increment word counter and move to next state
          state := s_MEM_REQ_NEXT
          missWordCounter := missWordCounter + 1.U
          // send read request for next word in the cache line
          memReqValidReg := true.B
          memReqAddrReg := cacheLineBaseAddr + ((missWordCounter + 1.U) << log2Ceil(dataWidth / 8).U)
          memReqIsWrite := false.B
          // memReadRespReadyReg := false.B // deassert ready until next word is requested
        }
      }
    }
    is(s_MEM_REQ_NEXT) {
      // send read request for next word in the cache line
      when(io.memReq.valid && io.memReq.ready) {
        state := s_MEM_RESP_WAIT
        memReqValidReg := false.B // deassert read request valid
        // memReadRespReadyReg := true.B // ready to receive memory response
      }
    }

  }
}

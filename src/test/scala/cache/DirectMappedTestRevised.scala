package cachematic.cache

import chisel3._
import chiseltest._
import chiseltest.simulator.WriteVcdAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DirectMappedTestRevised extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "DirectMapped L1 Cache (Revised)"

  // default parameters
  val addrWidth = 32
  val dataWidth = 32
  val cacheSize = 1024  // bytes
  val blockSize = 16    // bytes
  val wordsPerBlock = blockSize / (dataWidth / 8)

  // helper functions
  def cpuReadReq(c: DirectMapped, addr: UInt): Unit = {
    c.io.cpuReq.valid.poke(true.B)
    c.io.cpuReq.bits.addrRequest.poke(addr)
    c.io.cpuReq.bits.isWrite.poke(false.B)
    c.io.cpuRsp.ready.poke(true.B)
  }

  def cpuWriteReq(c: DirectMapped, addr: UInt, data: UInt): Unit = {
    c.io.cpuReq.valid.poke(true.B)
    c.io.cpuReq.bits.addrRequest.poke(addr)
    c.io.cpuReq.bits.dataRequest.poke(data)
    c.io.cpuReq.bits.isWrite.poke(true.B)
  }

  // --- Instruction Cache Tests ---

  it should "detect hits and misses correctly as ICache" in {
    test(new DirectMapped(CacheType.Instruction, cacheSize, blockSize))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>   // <-- Enable VCD dump
        // 1. first Address Access ; Cold Miss (cache is empty initially)
        // s_IDLE
        cpuReadReq(c, "x00000048".U) // Tag=0, Index=4(6b000100), Offset=8(4b1000) => wordOffset=2(2b10)
        c.clock.step(1)
        // s_MEM_REQ_START
        c.io.cpuReq.valid.poke(false.B) // Deassert request
        c.io.memReq.valid.expect(true.B) 
        c.io.memReq.bits.isWrite.expect(false.B) // Expect read request to memory
        c.io.memReq.bits.addrRequest.expect("x00000040".U) // Block-aligned address
        c.io.memReq.ready.poke(true.B)
        c.clock.step(1)
        // s_MEM_RESP_WAIT
        c.io.memReq.valid.expect(false.B)
        c.io.memRsp.ready.expect(true.B)
        // Simulate memory response
        c.io.memRsp.valid.poke(true.B) // Memory responds
        c.io.memRsp.bits.dataResponse.poke(1.U) // First word
        c.clock.step(1)
        
        for (i <- 1 until wordsPerBlock) {
          val data = i+1
          val addr = BigInt("00000040", 16) + (i * (dataWidth / 8))
          // s_MEM_REQ_NEXT
          c.io.memReq.valid.expect(true.B) // Next read request
          c.io.memReq.bits.isWrite.expect(false.B)
          c.io.memReq.bits.addrRequest.expect(addr.U) // Next word
          c.io.memReq.ready.poke(true.B)
          c.clock.step(1)
          // s_MEM_RESP_WAIT
          c.io.memReq.valid.expect(false.B)
          c.io.memRsp.ready.expect(true.B)
          // Simulate memory response
          c.io.memRsp.valid.poke(true.B) // Memory responds
          c.io.memRsp.bits.dataResponse.poke(data.U)
          c.clock.step(1)
          
        }
        // s_IDLE: all words in cache line received, now respond to CPU
        c.io.cpuRsp.valid.expect(true.B)
        c.io.cpuRsp.bits.dataResponse.expect(3.U) // wordOffset=2 => 3rd word in block        
        c.io.memRsp.valid.poke(false.B) // Deassert memory response
        c.io.memRsp.bits.dataResponse.poke(0.U)

        c.clock.step(1)

        // 2. Access same address again: Should HIT
        //    cause it was loaded into cache on previous miss
        cpuReadReq(c, "x00000048".U)
        c.clock.step(1)
        // s_READ_HIT
        c.io.cpuReq.valid.poke(false.B) // Deassert request
        c.io.cpuRsp.valid.expect(true.B) 
        c.io.cpuRsp.bits.dataResponse.expect(3.U)
        c.io.memReq.valid.expect(false.B) // No memory request
        c.clock.step(1)

        // 3. Access different address that maps to the SAME index but a different TAG
        //    This causes a "conflict miss" in a direct-mapped cache since only one tag per index
        cpuReadReq(c, "x1000004c".U) // Change TAG bits
        c.clock.step(1)
        // s_MISS_REQ_START
        c.io.cpuReq.valid.poke(false.B) // Deassert request
        c.io.memReq.valid.expect(true.B) 
        c.io.memReq.bits.isWrite.expect(false.B) // Expect read request to memory
        c.io.memReq.bits.addrRequest.expect("x10000040".U) // Block-aligned address
        c.io.memReq.ready.poke(true.B)
        c.clock.step(1)
        // s_MEM_RESP_WAIT
        c.io.memReq.valid.expect(false.B)
        c.io.memRsp.ready.expect(true.B)
        // Simulate memory response
        c.io.memRsp.valid.poke(true.B) // Memory responds
        c.io.memRsp.bits.dataResponse.poke("d101".U) // First word
        c.clock.step(1)
        for (i <- 1 until wordsPerBlock) {
          val data = 101 + i
          val addr = BigInt("10000040", 16) + (i * (dataWidth / 8))
          // s_MEM_REQ_NEXT
          c.io.memReq.valid.expect(true.B) // Next read request
          c.io.memReq.bits.isWrite.expect(false.B)
          c.io.memReq.bits.addrRequest.expect(addr.U) // Next word
          c.io.memReq.ready.poke(true.B)
          c.clock.step(1)
          // s_MEM_RESP_WAIT
          c.io.memReq.valid.expect(false.B)
          c.io.memRsp.ready.expect(true.B)
          // Simulate memory response
          c.io.memRsp.valid.poke(true.B) // Memory responds
          c.io.memRsp.bits.dataResponse.poke(data.U)
          c.clock.step(1)
        }
        // s_IDLE: all words in cache line received, now respond to CPU
        c.io.cpuRsp.valid.expect(true.B)
        c.io.cpuRsp.bits.dataResponse.expect("d104".U) // wordOffset=3 => 4th word in block        
        c.io.memRsp.valid.poke(false.B) // Deassert memory response
        c.io.memRsp.bits.dataResponse.poke(0.U)

        c.clock.step(1)

        // 4. Access addr in 3 but with different word index: HIT since it was just loaded
        cpuReadReq(c, "x10000048".U)
        c.clock.step(1)
        // s_READ_HIT
        c.io.cpuReq.valid.poke(false.B) // Deassert request
        c.io.cpuRsp.valid.expect(true.B)
        c.io.cpuRsp.bits.dataResponse.expect("d103".U)
        c.io.memReq.valid.expect(false.B) // No memory request
        c.clock.step(1)

        // 5. Access addr1 again but with different index: MISS
        cpuReadReq(c, "x00000058".U)
        c.clock.step(1)
        // s_MISS_REQ
        c.io.cpuReq.valid.poke(false.B) // Deassert request
        c.io.memReq.valid.expect(true.B) 
        c.io.memReq.bits.isWrite.expect(false.B) // Expect read request to memory
        c.io.memReq.bits.addrRequest.expect("x00000050".U) // Block-aligned address
        c.io.memReq.ready.poke(true.B)
        c.clock.step(1)
        // s_MEM_RESP_WAIT
        c.io.memReq.valid.expect(false.B)
        c.io.memRsp.ready.expect(true.B)
        // Simulate memory response
        c.io.memRsp.valid.poke(true.B) // Memory responds
        c.io.memRsp.bits.dataResponse.poke("d501".U) // First word
        c.clock.step(1)
        for (i <- 1 until wordsPerBlock) {
          val data = 501 + i
          val addr = BigInt("00000050", 16) + (i * (dataWidth / 8))
          // s_MEM_REQ_NEXT
          c.io.memReq.valid.expect(true.B) // Next read request
          c.io.memReq.bits.isWrite.expect(false.B)
          c.io.memReq.bits.addrRequest.expect(addr.U) // Next word
          c.io.memReq.ready.poke(true.B)
          c.clock.step(1)
          // s_MEM_RESP_WAIT
          c.io.memReq.valid.expect(false.B)
          c.io.memRsp.ready.expect(true.B)
          // Simulate memory response
          c.io.memRsp.valid.poke(true.B) // Memory responds
          c.io.memRsp.bits.dataResponse.poke(data.U)
          c.clock.step(1)
        }
        // s_IDLE: all words in cache line received, now respond to CPU
        c.io.cpuRsp.valid.expect(true.B)
        c.io.cpuRsp.bits.dataResponse.expect("d503".U) // wordOffset=2 => 3rd word in block        
        c.io.memRsp.valid.poke(false.B) // Deassert memory response
        c.io.memRsp.bits.dataResponse.poke(0.U)

        c.clock.step(1)
    }
  }

  it should "detect hits and misses correctly as DCache" in {
    test(new DirectMapped(CacheType.Data, cacheSize, blockSize))
      .withAnnotations(Seq(WriteVcdAnnotation)) {c => 
        // 1. Write to an address: Cold Miss
        cpuWriteReq(c, "x00000050".U, "x10".U) // Tag=0, Index=5, Offset=0
        c.clock.step(1)
        // s_MISS_REQ
        c.io.cpuReq.valid.poke(false.B) // Deassert request
        c.io.memReq.valid.expect(true.B) 
        c.io.memReq.bits.isWrite.expect(false.B) // Expect read request to memory
        c.io.memReq.bits.addrRequest.expect("x00000050".U) // Block-aligned address
        c.io.memReq.ready.poke(true.B)
        c.clock.step(1)
        // s_MEM_RESP_WAIT
        c.io.memReq.valid.expect(false.B)
        c.io.memRsp.ready.expect(true.B)
        // Simulate memory response
        c.io.memRsp.valid.poke(true.B) // Memory responds
        c.io.memRsp.bits.dataResponse.poke("d501".U) // First word
        c.clock.step(1)
        for (i <- 1 until wordsPerBlock) {
          val data = (i+1)*16
          val addr = BigInt("00000050", 16) + (i * (dataWidth / 8))
          // s_MEM_REQ_NEXT
          c.io.memReq.valid.expect(true.B) // Next read request
          c.io.memReq.bits.isWrite.expect(false.B)
          c.io.memReq.bits.addrRequest.expect(addr.U) // Next word
          c.io.memReq.ready.poke(true.B)
          c.clock.step(1)
          // s_MEM_RESP_WAIT
          c.io.memReq.valid.expect(false.B)
          c.io.memRsp.ready.expect(true.B)
          // Simulate memory response
          c.io.memRsp.valid.poke(true.B) // Memory responds
          c.io.memRsp.bits.dataResponse.poke(data.U)
          c.clock.step(1)
        }
        // s_IDLE: all words in cache line received
        // Now confirm write request
        c.io.memReq.valid.expect(true.B)
        c.io.memReq.bits.isWrite.expect(true.B)
        c.io.memReq.bits.addrRequest.expect("x00000050".U)  
        c.io.memReq.bits.dataRequest.expect("x10".U)     
        c.io.memRsp.valid.poke(false.B) // Deassert memory response
        c.io.memRsp.bits.dataResponse.poke(0.U)

        c.clock.step(1)

        // 2. Write to same address (different offset): Should HIT
        cpuWriteReq(c, "x00000054".U, "x123".U) // Offset=4
        c.clock.step(1)
        // s_WRITE_HIT
        c.io.cpuReq.valid.poke(false.B)
        c.io.memReq.valid.expect(true.B)
        c.io.memReq.bits.isWrite.expect(true.B)
        c.io.memReq.bits.addrRequest.expect("x00000054".U)
        c.io.memReq.bits.dataRequest.expect("x123".U)
        c.io.memReq.ready.poke(true.B) // ready to receive write request from cache
        c.clock.step(1)

        // 3. Read from first address: Should HIT and return updated data
        cpuReadReq(c, "x00000050".U)
        c.clock.step(1)
        // s_READ_HIT
        c.io.cpuReq.valid.poke(false.B)
        c.io.cpuRsp.valid.expect(true.B)
        c.io.cpuRsp.bits.dataResponse.expect("x10".U)
        c.clock.step(1)
      }
  }
}

// // See README.md for license details.

// package cachematic.cache

// import chisel3._
// import chiseltest._
// import chiseltest.simulator.WriteVcdAnnotation
// import org.scalatest.flatspec.AnyFlatSpec
// import org.scalatest.matchers.should.Matchers

// /**
//   * Unit test of top-level Direct Mapped Cache implementation
//   *
//   * From a terminal shell use:
//   * {{{
//   * sbt 'testOnly cachematic.cache.DirectMappedTest'
//   * }}}
//   *
//   * TODO: update arguments in DirectMapped constructor after subclassing SetAssociative
//   */
// class DirectMappedTest extends AnyFlatSpec with ChiselScalatestTester with Matchers {

//   behavior of "DirectMapped"

//   it should "detect hits and misses correctly" in {
//     test(new DirectMapped(cacheSizeBytes = 1024, blockSizeBytes = 16))
//       .withAnnotations(Seq(WriteVcdAnnotation)) { c =>   // <-- Enable VCD dump

//       // helper fun to apply address & clockstep
//       def sendAddr(addr: BigInt): Unit = {
//         c.io.address.poke(addr.U)
//         c.clock.step(1) // one cycle per access
//       }

//       // first Address Access ; Cold Miss (cache is empty initially)
//       val addr1 = BigInt("CAFEBABE", 16)  // random 32-bit address
//       sendAddr(addr1)
//       c.io.hit.expect(false.B)  // Expect a MISS because cache is empty
//       c.io.miss.expect(true.B)

//       // Access same address again: Should HIT
//       //    cause it was loaded into cache on previous miss
//       sendAddr(addr1)
//       c.io.hit.expect(true.B)   // expect HIT this time
//       c.io.miss.expect(false.B)

//       // Access different address that maps to the SAME index but a different TAG
//       //    This causes a "conflict miss" in a direct-mapped cache since only one tag per index
//       val addr2 = addr1 ^ BigInt("10000000", 16)  // Flip upper bits to change the tag
//       sendAddr(addr2)
//       c.io.hit.expect(false.B)  // MISS cause it evicts addr1's entry
//       c.io.miss.expect(true.B)

//       // Access addr2 again: HIT since it was just loaded
//       sendAddr(addr2)
//       c.io.hit.expect(true.B)   // HIT cause addr2 is now in cache
//       c.io.miss.expect(false.B)

//       // 5) Access addr1 again: MISS cause it was evicted earlier by addr2
//       sendAddr(addr1)
//       c.io.hit.expect(false.B)  // MISS again (direct-mapped limitation)
//       c.io.miss.expect(true.B)
//     }
//   }
// }

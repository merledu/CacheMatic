// package cachematic

// import chisel3._
// import chisel3.util._
// import cachematic.cache._   // for DirectMappedCache 
// object CacheAccessType {
//   val ReadOnly  = 0.U(2.W)  // icache 
//   val WriteOnly = 1.U(2.W)  // rarely used
//   val ReadWrite = 2.U(2.W)  // dcache 
// }


// class Cache(val cacheSizeBytes: Int, val blockSizeBytes: Int, val accessType: UInt) extends Module {

//   val addrWidth = 32
//   val dataWidth = 32

//   val io = IO(new Bundle {
//     val addr   = Input(UInt(addrWidth.W))
//     val rdata  = Output(UInt(dataWidth.W))
//     val valid  = Output(Bool())

//     val wdata  = Input(UInt(dataWidth.W))
//     val wen    = Input(Bool())
//   })

//   // direct-mapped technique
//   val dcache = Module(new DirectMapped(cacheSizeBytes, blockSizeBytes))

//   dcache.io.address := io.addr

//   // default outputs
//   io.rdata := 0.U
//   io.valid := false.B

//   // cache behavior depends on access type
//   when(accessType === CacheAccessType.ReadOnly) {  // Instruction cache reads only
//     io.rdata := dcache.io.rdata
//     io.valid := dcache.io.valid
//     dcache.io.wen   := false.B
//     dcache.io.wdata := 0.U
//   } .elsewhen(accessType === CacheAccessType.ReadWrite) { // Data cache
//     io.rdata := dcache.io.rdata
//     io.valid := dcache.io.valid
//     dcache.io.wen   := io.wen
//     dcache.io.wdata := io.wdata
//   } .elsewhen(accessType === CacheAccessType.WriteOnly) {
//     // Writeonly
//     dcache.io.wen   := io.wen
//     dcache.io.wdata := io.wdata
//   }
// }

package essent.passes

import firrtl._
import firrtl.ir._
import firrtl.passes._


object ReplaceAsyncRegs extends Pass {
  def desc = "Replaces AsyncResetReg (black-box) with non-external module that behaves the same"
  // this pass is inspired by firebox/sim/src/main/scala/passes/AsyncResetReg.scala

  def isCorrectAsyncRegModule(em: ExtModule): Boolean = {
    val nameCorrect = em.defname == "AsyncResetReg"
    val portNames = em.ports map { _.name }
    val portsCorrect = portNames == Seq("rst", "clk", "en", "q", "d")
    nameCorrect && portsCorrect
  }

  def generateReplacementModule(em: ExtModule): Module = {
    println(s"Replacing ${em.name} (${em.defname})")
    val oneBitType = UIntType(IntWidth(1))
    val zero = UIntLiteral(0,IntWidth(1))
    val reg = DefRegister(NoInfo, "r", oneBitType, WRef("clk", ClockType, PortKind, MALE), zero, zero)
    val resetMux = Mux(WRef("rst", oneBitType, PortKind, MALE),
                       zero,
                       WRef("enMux", oneBitType, NodeKind, MALE), oneBitType)
    val enableMux = Mux(WRef("en", oneBitType, PortKind, MALE),
                        WRef("d", oneBitType, PortKind, MALE),
                        WRef(reg), oneBitType)
    val enableMuxStmt = DefNode(NoInfo, "enMux", enableMux)
    val resetMuxStmt = DefNode(NoInfo, "resetMux", resetMux)
    val connectToReg = Connect(NoInfo, WRef(reg), WRef("resetMux", oneBitType, NodeKind, MALE))
    val connectFromReg = Connect(NoInfo, WRef("q", oneBitType, PortKind, FEMALE), WRef(reg))
    val bodyStmts = Seq(reg, enableMuxStmt, resetMuxStmt, connectToReg, connectFromReg)
    Module(em.info, em.name, em.ports, Block(bodyStmts))
  }

  def run(c: Circuit): Circuit = {
    val modulesx = c.modules.map {
      case em: ExtModule if isCorrectAsyncRegModule(em) => generateReplacementModule(em)
      case m => m
    }
    Circuit(c.info, modulesx, c.main)
  }
}
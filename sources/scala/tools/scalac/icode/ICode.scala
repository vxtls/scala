/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

import scalac.{Global => scalac_Global}

import scala.collection.mutable.HashMap;
import scala.collection.mutable.HashSet;

import scalac.symtab._;
import scalac.atree._;
import scalac.util.Debug;

package scala.tools.scalac.icode {

/** This class represents the intermediate code of a method*/
class ICode(label: String, global: scalac_Global) {

  //##################################################
  // Public fields

  /* The set of all blocks */
  val blocks: HashSet[IBasicBlock] = new HashSet;

  /* The start block of the method */
  var startBlock : IBasicBlock = null;

  /* The stack produced by this method */
  var producedStack : ICTypeStack = null;

  //##################################################
  // Private fields
  private var currentLabel : int = 0;

  private var aTreeLabels : HashMap[Symbol, IBasicBlock] = new HashMap;

  //##################################################
  // Constructor code

  startBlock = newBlock;
  startBlock.initStack(new ICTypeStack);

  //##################################################
  // Public methods

  /** This method produce the intermediate code for
    * the given ATree */
  def generate(acode : ACode) = {
    val ctx = gen(acode, new GenContext(startBlock, null));

    ctx.emit(RETURN()); // ??? RETURN instruction rather come from ATree
    genTypeStack;

    producedStack = ctx.currentBlock.endStack;
  }

  /* This method applies the given function to all the block */
  def icTraverse(f: IBasicBlock => unit) = {
    // ?? Define order (actually preorder)
    val visited : HashMap[IBasicBlock, boolean] = new HashMap;
    visited.incl(blocks.elements.map((x: IBasicBlock) => Pair(x, false)));

    var blockToVisit : List[IBasicBlock] = startBlock::Nil;

    while (blockToVisit != Nil) {
      blockToVisit match {
	case b::xs => {
	  if (!visited(b)) {
	    f(b);
	    blockToVisit = b.successors:::xs;
	    visited += b -> true;
	  } else
	    blockToVisit = xs;
	}
      }
    }
  }


  /** This methods returns a string representation of the ICode */
  override def toString() : String = "ICode '"+label+"'"; //

  //##################################################
  // Public method - printing (for debug use)

  /** This method print the code */
  def print() : unit = print(System.out);

  def print(out: java.io.PrintStream) : unit = {
    icTraverse((bb: IBasicBlock) => {
      out.println("Block #"+bb.label);
      out.println("Substituable variables : ");
      if (bb.substituteVars != null)
	bb.substituteVars.foreach(out.print);
      else
	out.println(" {Empty} ");
      out.println("Instructions:");
      bb.bbTraverse((ici: ICInstruction) =>
	out.println("  "+ici.toString()));
      out.print  ("Successors: ");
      bb.successors.foreach((bb: IBasicBlock) => out.print(bb.label+", "));
      out.println (""); // ?? Del
      out.println ();
    });
  }


  def logType = {
    global.log ("// Typing "+toString());
    this.icTraverse((bb: IBasicBlock) => {
      global.log ("Typing block #"+bb.label);
      var typer = new ICTypeStack;
      bb.bbTraverse((ic: ICInstruction) => {
	typer = typer.eval(ic);
	global.log(ic.toString()+" -> "+typer.toString());
      });

    });
   }

  //##################################################
  // Private methods

  /* Compute a unique new label */
  private def nextLabel = {
    currentLabel = currentLabel + 1;
    currentLabel;
  }

  /* This method :
   * 1/ create a new block
   * 2/ add the new block to the set
   * 3/ return the new block
   */
  private def newBlock : IBasicBlock = {
    val block = new IBasicBlock(nextLabel);
    blocks += block;
    block;
  }

  /* Generate the code from a given ATree */
  private def gen(aCode: ACode, ctx: GenContext) : GenContext  =
    aCode match {
      case ACode.Void => {
	global.log ("ICodeGenerator::emit: Void node found");
	ctx;
      }

      case code: ACode$This =>
	ctx.emit(THIS(code.clasz));

      case code: ACode$Constant =>
	ctx.emit(CONSTANT(code.constant));

      case code: ACode$Load =>
	code.location match {
	  case _: ALocation$Module => {
	    global.log ("ICodeGenerator::emit: Load(Module) node found");
	    ctx;
	  }

	  case loc: ALocation$Field => {
	    var ctx1 = gen(loc.`object`, ctx);
	    ctx.emit(LOAD_FIELD(loc.field, loc.isStatic));
	  }

	  case loc: ALocation$Local =>
	    ctx.emit(LOAD_LOCAL(loc.local, loc.isArgument));

	  case loc: ALocation$ArrayItem => {
	    var ctx1 = gen(loc.array, ctx);
	    ctx1 = gen(loc.index, ctx1);
	    ctx1.emit(LOAD_ARRAY_ITEM());
	  }
	}

      case code: ACode$Store =>
	code.location match {
	  case _: ALocation$Module => {
	    global.log ("ICodeGenerator::emit: Store(Module(_)) node found");
	    ctx;
	  }

	  case loc: ALocation$Field => {
	    var ctx1 = gen(loc.`object`, ctx);
	    ctx1 = gen(code.value, ctx1);
	    ctx1.emit(STORE_FIELD(loc.field, loc.isStatic));
	  }

	  case loc: ALocation$Local => {
	    val ctx1 = gen(code.value, ctx);
	    ctx1.emit(STORE_LOCAL(loc.local, loc.isArgument));
	  }

	  case loc: ALocation$ArrayItem => {
	    var ctx1 = gen(loc.array, ctx);
	    ctx1 = gen(loc.index, ctx1);
	    ctx1 = gen(code.value, ctx1);
	    ctx1.emit(STORE_ARRAY_ITEM());
	  }
	}

      case code: ACode$Apply =>
	if (code.function.isInstanceOf[AFunction$Method]) {
	  val fun = code.function.asInstanceOf[AFunction$Method];
	  if (fun.style == AInvokeStyle.New) {
	    val vargs_it = new IterableArray(code.vargs).elements;
	    val sym = fun.method;
	    // !!! Depend the backend in use
	    ctx.emit(NEW(sym.owner()));
	    ctx.emit(DUP(sym.owner().getType()));
	    var ctx1 = ctx;
	    vargs_it.foreach((varg: ACode) => ctx1 = gen(varg, ctx1));
	    ctx1.emit(CALL_METHOD(sym,AInvokeStyle.StaticInstance));
	  } else {
	    val vargs_it = new IterableArray(code.vargs).elements;
	    var ctx1 = ctx;
	    fun.style match {
	      case AInvokeStyle.StaticClass =>
		; // NOP
	      case _ =>
		ctx1 = gen(fun.`object`, ctx1);
	    }
	    vargs_it.foreach((varg: ACode) => ctx1 = gen(varg, ctx1));
	    ctx1.emit(CALL_METHOD(fun.method, fun.style));
	  }
	} else if (code.function.isInstanceOf[AFunction$Primitive]) {
	  val fun = code.function.asInstanceOf[AFunction$Primitive];
	  val vargs_it = new IterableArray(code.vargs).elements;
	  var ctx1 = ctx;

	  vargs_it.foreach((varg: ACode) => ctx1 = gen(varg, ctx1));
	  ctx1.emit(CALL_PRIMITIVE(fun.primitive));
	} else if (code.function.isInstanceOf[AFunction$NewArray]) {
	  val fun = code.function.asInstanceOf[AFunction$NewArray];
	  var ctx1 = gen(code.vargs(0), ctx); // The size is given as first argument
	  ctx1.emit(CREATE_ARRAY(fun.element));
	} else
	  throw new MatchError("ICode.scala", 0);

      case code: ACode$IsAs =>
	if (code.cast) {
	  var ctx1 = gen(code.value, ctx);
	  ctx1.emit(CHECK_CAST(code.`type`));
	} else {
	  var ctx1 = gen(code.value, ctx);
	  ctx1.emit(IS_INSTANCE(code.`type`));
	}

      case code: ACode$If => {
	genAlt(code.test, code.success, code.failure, ctx, newBlock);
      }

      case code: ACode$Switch => {

	val switchBodies = List.fromArray(code.bodies, 0, code.bodies.length);
	var switchBlocks: List[IBasicBlock] = Nil;
	for (val i : ACode <- switchBodies) switchBlocks = newBlock::switchBlocks;
	val switchPairs = switchBodies.zip(switchBlocks);

	val nextBlock = newBlock;

	var ctx1 = gen(code.test, ctx);
	ctx1.emit(SWITCH(code.tags, switchBlocks));
	ctx1.currentBlock.addSuccessors(switchBlocks);

	switchPairs.foreach((p: Pair[ACode, IBasicBlock]) => {
	  val code = p._1;
	  val block = p._2;

	  val ctx2 = gen(code, new GenContext(block, nextBlock));
	  ctx2.closeBlock;
	});

	ctx1.changeBlock(nextBlock);
      }

      case code: ACode$Synchronized => {
	var ctx1 = gen(code.lock, ctx);
	ctx1.emit(MONITOR_ENTER());
	ctx1 = gen(code.value,ctx);
	ctx1 = gen(code.lock, ctx);
	ctx1.emit(MONITOR_EXIT());
      }

      case code: ACode$Block => {
	val statements_it = new IterableArray(code.statements).elements;
	var ctx1 = ctx;
	statements_it.foreach((st: ACode) => ctx1 = gen(st, ctx1));
	ctx1 = gen(code.value, ctx1);
	ctx1;
      }

      case code: ACode$Label => {

	val loopBlock = newBlock;
	var ctx1 = ctx.changeBlock(loopBlock);
	ctx.nextBlock = loopBlock;
	ctx.closeBlock;

	aTreeLabels += code.label -> loopBlock;

	loopBlock.substituteVars = List.fromIterator((new IterableArray(code.locals)).elements);
	gen(code.value, ctx1);
      }

      case code: ACode$Goto => {

	val vargs_it = new IterableArray(code.vargs).elements;
	global.log("Current label mapping: "+aTreeLabels.keys.foreach((s: Symbol) => global.log(s.toString())));
	global.log("Looking for sym: "+code.label);
	val gotoBlock = aTreeLabels(code.label);
        var ctx1 = ctx;

	// Stack-> :
	vargs_it.foreach((varg: ACode) => ctx1 = gen(varg, ctx1));

	// Stack-> :varg0:varg1:...:vargn
	// We should have the same number of varg that substitute vars

	gotoBlock.substituteVars.reverse.foreach(
	  (varg: Symbol) => ctx1.emit(STORE_LOCAL(varg, false)) // ?? false
	);
	ctx1.nextBlock = gotoBlock;
	ctx1.closeBlock;

	ctx1;
      }

      case code: ACode$Return => {
	var ctx1 = gen(code.value, ctx);
	ctx1.emit(RETURN());
      }
      case code: ACode$Throw => {
	var ctx1 = gen(code.value, ctx);
	ctx1.emit(THROW());
      }

      case code: ACode$Drop => {
	var ctx1 = gen(code.value, ctx);
	//global.log("Type de Drop: "+typ+" = unboxed:"+typ.unbox() );
	if (! code.`type`.isSameAs(global.definitions.UNIT_TYPE()))
	  code.`type`.unbox() match { // !!! Hack
	    case Type$UnboxedType(TypeTags.UNIT) =>
	      global.log("it matches UNIT !"); // debug ; // NOP
	    case _ =>
	      ctx1.emit(DROP(code.`type`));
	  }
	  else
	  global.log("it matches SCALAC_UNIT :-(");
	ctx1;
      }
    }

  /* This method genrates an alternative. In the case of an if instruction
  * It looks at the kind of alternatives and creates new basic blocks
  *
  * @param nextBlock : represents the block where alternatives meet again*/
  private def genAlt (cond : ACode, success: ACode, failure: ACode, ctx: GenContext, nextBlock: IBasicBlock) : GenContext = {
    val successBlock = success match {
      case ACode.Void => nextBlock;

      case code: ACode$If => {
	val ctx1 = new GenContext(newBlock, null);
	val ctx2 = genAlt(code.test, code.success, code.failure, ctx1, nextBlock);
	//ctx2.closeBlock; // or close when CJUMP is emitted
	ctx1.currentBlock;
      }

      case _ => {
	val ctx1 = new GenContext(newBlock, nextBlock);
	val ctx2 = gen(success, ctx1);
	ctx2.closeBlock;
	ctx1.currentBlock;
      }
    }

    val failureBlock = failure match {
      case ACode.Void => nextBlock;

      case code: ACode$If => {
	val ctx1 = new GenContext(newBlock, null);
	val ctx2 = genAlt(code.test, code.success, code.failure, ctx1, nextBlock);
	//ctx2.closeBlock; // or close when CJUMP is emitted
	ctx1.currentBlock;
      }

      case _ => {
	val ctx1 = new GenContext(newBlock, nextBlock);
	val ctx2 = gen(failure, ctx1);
	ctx2.closeBlock;
	ctx1.currentBlock;
      }
    }

    var ctx1 = genCond(cond, successBlock, failureBlock, ctx);
    ctx1.changeBlock(nextBlock);
  }

  /* This methods generate the test and the jump instruction */
  private def genCond(cond: ACode, successBlock: IBasicBlock, failureBlock: IBasicBlock, ctx: GenContext) : GenContext= {
    var ctx1 = ctx;
    var handled = false;
    cond match {
      case code: ACode$Apply =>
	code.function match {
	  case fun: AFunction$Primitive =>
	    if (fun.primitive.isInstanceOf[APrimitive$Test]) {
	      val primitive = fun.primitive.asInstanceOf[APrimitive$Test];
	      ctx1 = gen(code.vargs(0),ctx1);
	      if (primitive.zero)
		ctx1.emit(CZJUMP(successBlock, failureBlock, primitive.op));
	      else {
		ctx1 = gen(code.vargs(1), ctx1);
		ctx1.emit(CJUMP(successBlock, failureBlock, primitive.op));
	      }
	      ctx1.currentBlock.addSuccessors(successBlock::failureBlock::Nil);
	      handled = true;
	    } else if (fun.primitive.isInstanceOf[APrimitive$Negation]) {
	      ctx1 = genCond(code.vargs(0), failureBlock, successBlock, ctx1);
	      handled = true;
	    }
	  case _ =>
	}
      case _ =>
    }
    if (!handled) {
      ctx1 = gen(cond, ctx1);
      ctx1.emit(CONSTANT(AConstant.INT(1)));
      ctx1.emit(CJUMP(successBlock, failureBlock, ATestOp.EQ));
      ctx1.currentBlock.addSuccessors(successBlock::failureBlock::Nil);
    }
    ctx1;
  }

  /* This method generate the type stacks of all the blocks
   * of this method.*/
  private def genTypeStack = {
    icTraverse((bb: IBasicBlock) => {
      global.log("// Typing block #"+bb.label);
      global.log("\t-> "+bb.endStack);
      bb.typeBlock;
      // !!! Here we must test if all the meeting blocks have the
      // !!! same stack.
      bb.successors.foreach((suc: IBasicBlock) => suc.initStack(bb.endStack));
    });
  }

}

  /* This class represents the context of generating icode */
  class GenContext(current: IBasicBlock, next: IBasicBlock) {

    // ##################################################
    // Public fields

    /* The block in which we add instructions */
    val currentBlock = current;

    /* The block were we jump at the end of the current block
     * It can be the *null* value */
    var nextBlock    = next;

    // ##################################################
    // Constructor code

    assert(current != null, "No current block");

    // ##################################################
    // Public methods

    /* append an instruction at the end of the current block */
    def emit(instr: ICInstruction) : GenContext = {
      currentBlock.emit(instr);
      this;
    }

    /* Split the current block. The next block is still the same */
    def changeBlock(bb: IBasicBlock) = {
      new GenContext(bb, next);
    }

    /* Close the current block. If there's a known next block
     * It append a jump instruction to the block.
     * This method do something only the first time it's called */
    def closeBlock = {
      if (!currentBlock.isClosedBlock) {
	if (nextBlock != null) {
	  emit(JUMP(nextBlock));
	  currentBlock.addSuccessor(nextBlock);
	}
	currentBlock.close;
      }
    }
  }

} // package

/* NSC -- new scala compiler
 * Copyright 2005 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id$
package scala.tools.nsc.util;

object NameTransformer {
  private val nops = 128;
  private val ncodes = 26 * 26;

  private class OpCodes(val op: char, val code: String, val next: OpCodes);

  private val op2code = new Array[String](nops);
  private val code2op = new Array[OpCodes](ncodes);

  private def enterOp(op: char, code: String) = {
    op2code(op) = code;
    val c = (code.charAt(1) - 'a') * 26 + code.charAt(2) - 'a';
    code2op(c) = new OpCodes(op, code, code2op(c))
  }

  enterOp('~', "$tilde");
  enterOp('=', "$eq");
  enterOp('<', "$less");
  enterOp('>', "$greater");
  enterOp('!', "$bang");
  enterOp('#', "$hash");
  enterOp('%', "$percent");
  enterOp('^', "$up");
  enterOp('&', "$amp");
  enterOp('|', "$bar");
  enterOp('*', "$times");
  enterOp('/', "$div");
  enterOp('+', "$plus");
  enterOp('-', "$minus");
  enterOp(':', "$colon");
  enterOp('\\', "$bslash");
  enterOp('?', "$qmark");
  enterOp('@', "$at");

  /** Replace operator symbols by corresponding "$op_name" */
  def encode(name: String): String = {
    var buf: StringBuffer = null;
    val len = name.length();
    var i = 0;
    while (i < len) {
      val c = name charAt i;
      if (c < nops && op2code(c) != null) {
        if (buf == null) {
          buf = new StringBuffer();
          buf.append(name.substring(0, i));
        }
        buf.append(op2code(c));
      } else if (c >= nops) {
        if (buf == null) {
          buf = new StringBuffer();
          buf.append(name.substring(0, i));
        }
        buf.append("$u");
        val hex = java.lang.Integer.toHexString(c);
        var j = hex.length();
        while (j < 4) {
          buf.append('0');
          j = j + 1
        }
        buf.append(hex);
      } else if (buf != null) {
        buf.append(c)
      }
      i = i + 1
    }
    if (buf == null) name else buf.toString()
  }

  /** Replace $op_name by corresponding operator symbol */
  def decode(name0: String): String = {
    //System.out.println("decode: " + name);//DEBUG
    val name = if (name0.endsWith("<init>")) name0.substring(0, name0.length() - ("<init>").length()) + "this";
               else name0;
    var buf: StringBuffer = null;
    val len = name.length();
    var i = 0;
    while (i < len) {
      var ops: OpCodes = null;
      val c = name charAt i;
      if (c == '$' && i + 2 < len) {
	val ch1 = name.charAt(i+1);
	if ('a' <= ch1 && ch1 <= 'z') {
	  val ch2 = name.charAt(i+2);
	  if ('a' <= ch2 && ch2 <= 'z') {
	    ops = code2op((ch1 - 'a') * 26 + ch2 - 'a');
	    while (ops != null && !name.startsWith(ops.code, i)) ops = ops.next;
	    if (ops != null) {
              if (buf == null) {
		buf = new StringBuffer();
		buf.append(name.substring(0, i));
              }
              buf.append(ops.op);
	      i = i + ops.code.length()
	    }
	  }
	}
        if (ops == null && ch1 == 'u' && i + 5 < len) {
          var value = 0;
          var j = 2;
          while (j < 6 && value >= 0) {
            val digit = Character.digit(name.charAt(i + j), 16);
            if (digit < 0) value = -1
            else value = (value << 4) + digit;
            j = j + 1
          }
          if (value >= 0) {
            if (buf == null) {
              buf = new StringBuffer();
              buf.append(name.substring(0, i));
            }
            buf.append(value.asInstanceOf[char]);
            ops = new OpCodes(0.asInstanceOf[char], "", null);
            i = i + 6
          }
        }
      }
      if (ops == null) {
	if (buf != null) buf.append(c);
	i = i + 1
      }
    }
    //System.out.println("= " + (if (buf == null) name else buf.toString()));//DEBUG
    if (buf == null) name else buf.toString()
  }
}

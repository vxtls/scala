/*     ___ ____ ___   __   ___   ___
**    / _// __// _ | / /  / _ | / _ \    Scala classfile decoder
**  __\ \/ /__/ __ |/ /__/ __ |/ ___/    (c) 2003, LAMP/EPFL
** /____/\___/_/ |_/____/_/ |_/_/
**
**  $Id$
*/

package scala.tools.scalap;

import java.io._;
import scala.collection._;


object Main {

    val VERSION = "1.0a";

    var verbose = false;

    def usage: Unit = {
        scala.Console.println("usage: scalap {<option>} <name>");
        scala.Console.println("where <option> is");
        scala.Console.println("  -private               print private definitions");
        scala.Console.println("  -verbose               print out additional information");
        scala.Console.println("  -version               print out the version number of scalap");
        scala.Console.println("  -help                  display this usage message");
        scala.Console.println("  -classpath <pathlist>  specify where to find user class files");
        scala.Console.println("  -cp <pathlist>         specify where to find user class files");
    }

    def process(args: Arguments, path: ClassPath)(classname: String): Unit = {
		val file = path.openClass(Names.encode(
			if (classname == "scala.AnyRef") "java.lang.Object" else classname));
		if (file.exists) {
			if (verbose)
				scala.Console.println(scala.Console.BOLD + "FILENAME" + scala.Console.RESET +
				                " = " + file.getPath);
			val reader = new ByteArrayReader(file.content);
			val clazz = new Classfile(reader);
			val attrib = clazz.attribs.find(a => a.toString() == "ScalaSignature");
			attrib match {
				case Some(a) =>
					val info = new ScalaAttribute(a.reader);
					val symtab = new EntityTable(info);
					val out = new OutputStreamWriter(System.out);
					val writer = new ScalaWriter(args, out);
					symtab.root.elements foreach (
						sym => { writer.printSymbol(sym);
								 writer.println*; });
					out.flush();
				case None =>
				    val out = new OutputStreamWriter(System.out);
				    val writer = new JavaWriter(clazz, out);
				    writer.printClass;
				    out.flush();
			}
		} else if (classname == "scala.Any") {
			scala.Console.println("package scala;");
			scala.Console.println("class Any {");
			scala.Console.println("    def eq(scala.Any): scala.Boolean;");
			scala.Console.println("    final def ==(scala.Any): scala.Boolean;");
			scala.Console.println("    final def !=(scala.Any): scala.Boolean;");
			scala.Console.println("    def equals(scala.Any): scala.Boolean;");
			scala.Console.println("    def hashCode(): scala.Int;");
			scala.Console.println("    def toString(): java.lang.String;");
			scala.Console.println("    final def isInstanceOf[T]: scala.Boolean;");
			scala.Console.println("    final def asInstanceOf[T]: T;");
			scala.Console.println("    def match[S, T](f: S => T): T;");
			scala.Console.println("}");
		} else if (classname == "scala.All") {
			scala.Console.println("Type scala.All is artificial; it is a subtype of all types.");
		} else if (classname == "scala.AllRef") {
			scala.Console.println("Type scala.AllRef is artificial; it is a subtype of all subtypes of scala.AnyRef.");
		} else
			scala.Console.println("class/object not found.");
    }

    def main(args: Array[String]) = {
        if (args.length == 0)
            usage;
        else {
        	val arguments = Arguments.Parser('-')
        	                         .withOption("-private")
        		                     .withOption("-verbose")
        		                     .withOption("-version")
        		                     .withOption("-help")
        		                     .withOptionalArg("-classpath")
        		                     .withOptionalArg("-cp")
                                     .parse(args);
            if (arguments contains "-version")
                scala.Console.println("scalap " + VERSION);
            if (arguments contains "-help")
            	usage;
            verbose = arguments contains "-verbose";
            val path = arguments.getArgument("-classpath") match {
            	case None => arguments.getArgument("-cp") match {
            		case None => new ClassPath
            		case Some(path) => new ClassPath { override val classPath = path }
            	}
            	case Some(path) => new ClassPath { override val classPath = path }
            }
            if (verbose)
            	scala.Console.println(scala.Console.BOLD + "CLASSPATH" + scala.Console.RESET +
            	                " = " + path);
           	arguments.getOthers.foreach(process(arguments, path));
        }
    }
}

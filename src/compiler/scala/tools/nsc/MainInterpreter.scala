/* NSC -- new Scala compiler
 * Copyright 2005-2006 LAMP/EPFL
 * @author emir
 */
// $Id$
package scala.tools.nsc

import java.io.{BufferedReader, FileReader, IOException, InputStreamReader}
import scala.tools.nsc.util.{Position}
import scala.tools.nsc.reporters.{Reporter, ConsoleReporter}

/** The main class for the new scala interpreter.
 */
object MainInterpreter {
  val reporter = new ConsoleReporter()

  var interpreter: Interpreter = _

  /** print a friendly help message */
  def printHelp = {
    scala.Console.println("This is an interpreter for Scala.")
    scala.Console.println("Type in expressions to have them evaluated.")
    scala.Console.println("Type :quit to exit the interpreter.")
    scala.Console.println("Type :compile followed by a filename to compile a complete Scala file.")
    scala.Console.println("Type :load followed by a filename to load a sequence of interpreter commands.")
    scala.Console.println("Type :help to repeat this message later.")
  }

  /** The main read-eval-print loop for the interpereter.  It calls
      command() for each line of input, and stops when command()
      returns false */
  def repl: Unit = {
    val in = new BufferedReader(new InputStreamReader(System.in))

    while(true) {
      scala.Console.print("\nscala> ")
      var line = in.readLine()
      if(line == null)
        return ()  // assumes null means EOF

      val keepGoing = command(line)
      if(!keepGoing)
        return ()  // the evpr function said to stop
    }
  }

  /** interpret one line of code submitted by the user */
  def interpretOne(line: String): Unit = {
    try {
      interpreter.interpret(line)
    } catch {
      case e: Exception =>
        reporter.info(null, "Exception occurred: " + e.getMessage(), true)
        //e.printStackTrace()
    }
  }

  /** interpret all lines from a specified file */
  def interpretAllFrom(filename: String): Unit = {
    val fileIn = try {
      new FileReader(filename)
    } catch {
      case _:IOException =>
        scala.Console.println("Error opening file: " + filename)
        null
    }
    if (fileIn == null) return ()
    val in = new BufferedReader(fileIn)
    while(true) {
      val line = in.readLine
      if (line == null) {
        fileIn.close
        return ()
      }
      command(line)
    }
  }


  /** run one command submitted by the user */
  def command(line: String): Boolean = {
    def withFile(command: String)(action: String => Unit): Unit = {
      val spaceIdx = command.indexOf(' ')
      if (spaceIdx <= 0) {
        scala.Console.println("That command requires a filename to be specified.")
        return ()
      }
      val filename = command.substring(spaceIdx).trim
      action(filename)
    }

    if (line.startsWith(":"))
      line match {
        case ":help" => printHelp
        case ":quit" => return false
        case _ if line.startsWith(":compile") => withFile(line)(f => interpreter.compile(f))
        case _ if line.startsWith(":load") => withFile(line)(f => interpretAllFrom(f))
        case _ => scala.Console.println("Unknown command.  Type :help for help.")
      }
    else if(line.startsWith("#!/")) // skip the first line of Unix scripts
    	()
    else if(line.startsWith("exec scalaint ")) // skip the second line of Unix scripts
      ()
    else
      interpretOne(line)
    true
  }


	/** process command-line arguments and do as they request */
  def main(args: Array[String]): unit = {
    val command = new InterpreterCommand(List.fromArray(args), error)

    reporter.prompt = command.settings.prompt.value
    if (command.settings.help.value) {
      reporter.info(null, command.usageMsg, true)
      return ()
    }

    val compiler = new Global(command.settings, reporter)
    interpreter = new Interpreter(compiler, str=>scala.Console.print(str))

    try {
      if(!command.files.isEmpty) {
        interpreter.beQuiet
        command.files match {
          case List(filename) => interpretAllFrom(filename)
          case _ => scala.Console.println(
              "Sorry, arguments to interpreter scripts are not currently supported.")
        }
      } else {
        printHelp
        repl
      }
    } finally {
      interpreter.close
    }
  }

}

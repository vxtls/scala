############################################################-*-Makefile-*-####
# JC - Compile Java Files
##############################################################################
# $Id$

##############################################################################
# Usage
#
#   make jc [target=<target>] {<VARIABLE>=<value>}
#
##############################################################################
# Variables
#
# JC_COMPILER		 = compiler name, for example JAVAC
# $(JC_COMPILER)	 = compiler command
# $(JC_COMPILER)_FLAGS	 = compiler-specific compilation flags
# JC_FLAGS		+= compilation flags
# JC_CLASSPATH		 = location of user class files
# JC_SOURCEPATH		 = location of input source files
# JC_BOOTCLASSPATH	 = location of bootstrap class files
# JC_EXTDIRS		 = location of installed extensions
# JC_OUTPUTDIR		 = directory where to place generated class files
# JC_ENCODING		 = character encoding used by source files
# JC_SOURCE		 = version of source code
# JC_TARGET		 = version of target bytecode
# JC_FILES		+= files to compile
#
# All variables may have target specific values which override the
# normal value. Those values are specified by variables whose name is
# prefixed with the target name. For example, to override the value of
# the variable JC_CLASSPATH with target COMPILER, one may define the
# variable COMPILER_JC_CLASSPATH.
#
##############################################################################
# Examples
#
# Compile file "sources/scalac/Main.java"
#
#   make jc JC_FILES=sources/scalac/Main.java
#
#
# Compile the whole runtime
#
#   make jc target=RUNTIME
#
#
# A Makefile rule that compiles all modified files from the runtime
#
#   .latest-runtime		: $(RUNTIME_JC_FILES)
#           @$(MAKE) jc target=RUNTIME RUNTIME_JC_FILES='$?'
#           touch $@
#
##############################################################################

##############################################################################
# Defaults

JC_COMPILER		?= JAVAC
CYGWIN_PATH		?= $(1)
CYGWIN_FILE		?= $(1)
JAVAC			?= javac

##############################################################################
# Values

jc_COMPILER		 = $(call JC_LOOKUP,JC_COMPILER)
jc_compiler		 = $(call JC_LOOKUP,$(jc_COMPILER))
jc_compiler_flags	 = $(call JC_LOOKUP,$(jc_COMPILER)_FLAGS)
jc_FLAGS		 = $(call JC_LOOKUP,JC_FLAGS)
jc_CLASSPATH		 = $(call JC_LOOKUP,JC_CLASSPATH)
jc_SOURCEPATH		 = $(call JC_LOOKUP,JC_SOURCEPATH)
jc_BOOTCLASSPATH	 = $(call JC_LOOKUP,JC_BOOTCLASSPATH)
jc_EXTDIRS		 = $(call JC_LOOKUP,JC_EXTDIRS)
jc_OUTPUTDIR		 = $(call JC_LOOKUP,JC_OUTPUTDIR)
jc_ENCODING		 = $(call JC_LOOKUP,JC_ENCODING)
jc_SOURCE		 = $(call JC_LOOKUP,JC_SOURCE)
jc_TARGET		 = $(call JC_LOOKUP,JC_TARGET)
jc_FILES		 = $(call JC_LOOKUP,JC_FILES)
jc_PREREQS		 = $(call JC_LOOKUP,JC_PREREQS)

##############################################################################
# Command

jc			+= $(jc_compiler)
jc			+= $(jc_compiler_flags)
jc			+= $(jc_FLAGS)
jc			+= $(jc_CLASSPATH:%=-classpath $(call CYGWIN_PATH,%))
jc			+= $(jc_SOURCEPATH:%=-sourcepath $(call CYGWIN_PATH,%))
jc			+= $(jc_BOOTCLASSPATH:%=-bootclasspath $(call CYGWIN_PATH,%))
jc			+= $(jc_EXTDIRS:%=-extdirs $(call CYGWIN_PATH,%))
jc			+= $(jc_OUTPUTDIR:%=-d $(call CYGWIN_FILE,%))
jc			+= $(jc_ENCODING:%=-encoding %)
jc			+= $(jc_SOURCE:%=-source %)
jc			+= $(jc_TARGET:%=-target %)
jc			+= $(jc_FILES:%=$(call CYGWIN_FILE,'%'))

##############################################################################
# Functions

JC_LOOKUP		 = $(if $($(target)_$(1)),$($(target)_$(1)),$($(1)))

##############################################################################
# Rules

jc		:
	@if ! compiler_version=`"$(jc_compiler)" -version 2>&1`; then \
	    echo "$$compiler_version" >&2; \
	    exit 1; \
	fi; \
	case "$$compiler_version" in \
	    javac\ 1.8.*|javac\ 8.*) ;; \
	    *) \
	        echo "Target $(target) requires JDK 8; found $$compiler_version" >&2; \
	        echo "Set SCALA_JAVA_HOME or JAVA_HOME to a JDK 8 installation." >&2; \
	        exit 1; \
	        ;; \
	esac
	@for prereq in $(jc_PREREQS); do \
	    if [ ! -f "$$prereq" ]; then \
	        echo "Missing local prerequisite for target $(target): $$prereq" >&2; \
	        echo "Bootstrap no longer downloads compiler dependencies automatically; provide the jar locally." >&2; \
	        exit 1; \
	    fi; \
	done
	@[ -d "$(jc_OUTPUTDIR)" ] || $(MKDIR) -p "$(jc_OUTPUTDIR)"
	$(strip $(jc))

.PHONY		: jc

##############################################################################

# -- VARIABLES --
# (locations)
SRC=src
TEST_SRC=test/src
LIB=etc
BUILD=bin
TEST_BUILD=test/bin
DIST=dist
TMP=tmp
# (classpath)
CP=${LIB}/jchart2d.jar:${LIB}/mysql.jar:${LIB}/postgresql.jar:${LIB}/scala-compiler.jar:${LIB}/scala-library.jar:${LIB}/sqlite.jar:${LIB}/breeze-math.jar
JAVANLP=${JAVANLP_HOME}/projects/core/classes:${JAVANLP_HOME}/projects/more/classes:${JAVANLP_HOME}/projects/core/lib/xom-1.2.6.jar
# (compilers)
SCALAC=scalac
# (config)
.SUFFIXES: .java .class

.java.class:
	${JDK6}/bin/javac $<

# -- JARS --
DIST_LIB=lib
${DIST}/${DIST_LIB}.jar: $(wildcard ${SRC}/org/goobs/database/*.java) $(wildcard ${SRC}/org/goobs/database/*.scala) $(wildcard ${SRC}/org/goobs/exec/*.java) $(wildcard ${SRC}/org/goobs/exec/*.scala) $(wildcard ${SRC}/org/goobs/graphics/*.java) $(wildcard ${SRC}/org/goobs/graphics/*.scala) $(wildcard ${SRC}/org/goobs/net/*.java) $(wildcard ${SRC}/org/goobs/net/*.scala) $(wildcard ${SRC}/org/goobs/io/*.java) $(wildcard ${SRC}/org/goobs/io/*.scala) $(wildcard ${SRC}/org/goobs/nlp/*.java) $(wildcard ${SRC}/org/goobs/nlp/*.scala) $(wildcard ${SRC}/org/goobs/scheme/*.java) $(wildcard ${SRC}/org/goobs/scheme/*.scala) $(wildcard ${SRC}/org/goobs/stanford/*.java) $(wildcard ${SRC}/org/goobs/stanford/*.scala) $(wildcard ${SRC}/org/goobs/stats/*.java) $(wildcard ${SRC}/org/goobs/stats/*.scala) $(wildcard ${SRC}/org/goobs/testing/*.java) $(wildcard ${SRC}/org/goobs/testing/*.scala) $(wildcard ${SRC}/org/goobs/util/*.java) $(wildcard ${SRC}/org/goobs/util/*.scala)
	@echo "--------------------------------------------------------------------------------"
	@echo " LIBRARY"
	@echo "--------------------------------------------------------------------------------"
	mkdir -p ${BUILD}
	mkdir -p ${DIST}
	#(compile)
	${JDK6}/bin/javac -Xlint:unchecked -Xlint:deprecation -d $(BUILD) -cp $(CP):${JAVANLP} `find $(SRC) -name "*.java"`
	${SCALAC} -deprecation -d ${BUILD} -cp ${CP}:${JAVANLP} `find ${SRC} -name "*.scala"` `find ${SRC} -name "*.java"`
	#(copy)
	cp ${SRC}/org/goobs/testing/mteval-v13a.pl ${BUILD}/org/goobs/testing/mteval-v13a.pl
	cp ${SRC}/org/goobs/util/lib.conf ${BUILD}/org/goobs/util/lib.conf
	cp ${SRC}/org/goobs/scheme/stdlib.scm ${BUILD}/org/goobs/util/stdlib.scm
	#(jar)
	jar cf ${DIST}/${DIST_LIB}.jar -C $(BUILD) .
	jar uf ${DIST}/${DIST_LIB}.jar -C $(SRC) .
	
DIST_DB=database
${DIST}/${DIST_DB}.jar: $(wildcard ${SRC}/org/goobs/database/*.java) ${SRC}/org/goobs/util/Utils.java ${SRC}/org/goobs/util/MetaClass.java ${SRC}/org/goobs/util/Decodable.java ${SRC}/org/goobs/io/Console.java ${SRC}/org/goobs/io/TextConsole.java
	@echo "--------------------------------------------------------------------------------"
	@echo " QRY (database)"
	@echo "--------------------------------------------------------------------------------"
	mkdir -p ${BUILD}
	mkdir -p ${DIST}
	${JDK6}/bin/javac -Xlint:unchecked -Xlint:deprecation -d $(BUILD) -cp $(CP) ${SRC}/org/goobs/database/*.java ${SRC}/org/goobs/util/Utils.java ${SRC}/org/goobs/util/MetaClass.java ${SRC}/org/goobs/util/Decodable.java ${SRC}/org/goobs/io/Console.java ${SRC}/org/goobs/io/TextConsole.java
	jar cf ${DIST}/${DIST_DB}.jar -C $(BUILD) .
	jar uf ${DIST}/${DIST_DB}.jar -C $(SRC) .

DIST_TEST=lib-test
${DIST}/${DIST_TEST}.jar: $(wildcard ${TEST_SRC}/org/goobs/tests/*.java) $(wildcard ${TEST_SRC}/org/goobs/tests/*.scala) ${DIST}/${DIST_LIB}.jar
	@echo "--------------------------------------------------------------------------------"
	@echo " TESTS"
	@echo "--------------------------------------------------------------------------------"
	mkdir -p ${TEST_BUILD}
	mkdir -p ${DIST}
	${JDK6}/bin/javac -Xlint:unchecked -Xlint:deprecation -d $(TEST_BUILD) -cp $(CP):${DIST}/${DIST_LIB}.jar:${LIB}/junit.jar `find $(TEST_SRC) -name "*.java"`
	${SCALAC} -deprecation -d $(TEST_BUILD) -cp $(CP):${DIST}/${DIST_LIB}.jar:${LIB}/junit.jar:${LIB}/scalatest.jar `find $(TEST_SRC) -name "*.scala"`
	jar cf ${DIST}/${DIST_TEST}.jar -C $(TEST_BUILD) .
	jar uf ${DIST}/${DIST_TEST}.jar -C $(TEST_SRC) .

# -- TARGETS --
lib: ${DIST}/${DIST_LIB}.jar 
db: ${DIST}/${DIST_DB}.jar
test: ${DIST}/${DIST_TEST}.jar
default: lib

all: ${DIST}/${DIST_LIB}.jar ${DIST}/${DIST_DB}.jar ${DIST}/${DIST_TEST}.jar

clean:
	rm -rf ${BUILD}
	rm -rf ${TEST_BUILD}
	rm -rf ${DIST}
	rm -f java.hprof.txt

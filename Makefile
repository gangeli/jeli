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
CP=${LIB}/jchart2d.jar:${LIB}/mysql.jar:${LIB}/postgresql.jar:${LIB}/scala-compiler.jar:${LIB}/scala-library.jar:${LIB}/sqlite.jar
JAVANLP=${JAVANLP_HOME}/projects/core/classes:${JAVANLP_HOME}/projects/more/classes:${JAVANLP_HOME}/projects/core/lib/xom-1.2.6.jar
# (config)
.SUFFIXES: .java .class

.java.class:
	javac $<

# -- JARS --
DIST_LIB=lib
${DIST}/${DIST_LIB}.jar: $(wildcard ${SRC}/*.java)	$(wildcard ${SRC}/*.scala)
	mkdir -p ${BUILD}
	mkdir -p ${DIST}
	javac -Xlint:unchecked -Xlint:deprecation -d $(BUILD) -cp $(CP):${JAVANLP} `find $(SRC) -name "*.java"`
	scalac -deprecation -d ${BUILD} -cp ${CP}:${JAVANLP} `find ${SRC} -name "*.scala"` `find ${SRC} -name "*.java"`
	jar cf ${DIST}/${DIST_LIB}.jar -C $(BUILD) .
	jar uf ${DIST}/${DIST_LIB}.jar -C $(SRC) .
	
DIST_DB=database
${DIST}/${DIST_DB}.jar: $(wildcard ${SRC}/org/goobs/database/*.java) ${SRC}/org/goobs/utils/Utils.java ${SRC}/org/goobs/utils/MetaClass.java ${SRC}/org/goobs/utils/Decodable.java ${SRC}/org/goobs/io/Console.java ${SRC}/org/goobs/io/TextConsole.java
	mkdir -p ${BUILD}
	mkdir -p ${DIST}
	javac -Xlint:unchecked -Xlint:deprecation -d $(BUILD) -cp $(CP) `find $(SRC) -name "*.java"`
	jar cf ${DIST}/${DIST_DB}.jar -C $(BUILD) .
	jar uf ${DIST}/${DIST_DB}.jar -C $(SRC) .

DIST_TEST=test
${DIST}/${DIST_TEST}.jar: $(wildcard ${TEST_SRC}/*.java)
	mkdir -p ${TEST_BUILD}
	mkdir -p ${DIST}
	javac -Xlint:unchecked -Xlint:deprecation -d $(TEST_BUILD) -cp $(CP):${DIST}/${DIST_LIB}.jar:${LIB}/junit.jar `find $(TEST_SRC) -name "*.java"`
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

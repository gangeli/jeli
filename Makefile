BUILD = bin

STANFORDJAR = /home/gabor/lib/java/stanford.jar

default: build

build: 
	ant build

tar:
	ant dist

test: build
	${BUILD}/aux/setupTest.rb
	ant test

clean:
	ant clean

ubuntu_10_10:
	#--Tools For Makefile
	#(programs)
	apt-get --assume-yes install python-software-properties
	#(ensure version)
	cat /etc/issue | egrep " 10.10(\.| )"
	#(ensure variables)
	mkdir -p etc
	cd etc && file ${STANFORDJAR}
	#--Java/Scala
	#(ensure java)
	/usr/lib/jvm/java-6-sun/bin/java > /dev/null || cat /etc/apt/sources.list | grep "deb http://archive.canonical.com/ maverick partner" || add-apt-repository "deb http://archive.canonical.com/ maverick partner"
	/usr/lib/jvm/java-6-sun/bin/java > /dev/null || apt-get update
	/usr/lib/jvm/java-6-sun/bin/java > /dev/null || apt-get --assume-yes install sun-java6-jdk
	#(ensure scala)
	apt-get --assume-yes install ant scala
	#--Java Dependencies
	#(ensure scala-libs)
	rm -f etc/scala-compiler.jar && ln -s /usr/share/java/scala-compiler.jar etc/scala-compiler.jar
	rm -f etc/scala-library.jar && ln -s /usr/share/java/scala-library.jar etc/scala-library.jar
	#(ensure databases)
	apt-get --assume-yes install libpg-java
	rm -f etc/postgresql.jar && ln -s /usr/share/java/postgresql.jar etc/postgresql.jar
	apt-get --assume-yes install libmysql-java
	rm -f etc/mysql.jar && ln -s /usr/share/java/mysql.jar etc/mysql.jar
	file etc/sqlite.jar
	#(ensure arduino)
	apt-get --assume-yes install librxtx-java
	rm -f etc/RXTXcomm.jar && ln -s /usr/share/java/RXTXcomm.jar etc/RXTXcomm.jar
	#(ensure stanford)
	rm -f etc/stanford.jar && ln -s ${STANFORDJAR} etc/stanford.jar
	rm -f etc/stanford-pos.jar && ln -s /home/gabor/lib/java/stanford-pos.jar etc/stanford-pos.jar
	#--Postgres
	sudo apt-get --assume-yes install postgresql

ubuntu: ubuntu_10_10

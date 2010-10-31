BUILD = bin

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

ubuntu-common:
	apt-get install scala
	add-apt-repository ppa:arduino-ubuntu-team
	apt-get update
	apt-get install librxtx-java arduino

ubuntu-ruby1.9: ubuntu-common
	apt-get install libmysql-ruby1.9.1 libpgsql-ruby1.9.1

ubuntu-ruby1.8: ubuntu-common
	apt-get install libmysql-ruby libpgsql-ruby

ubuntu: ubuntu-ruby1.9 ubuntu-ruby1.8

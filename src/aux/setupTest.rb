#!/usr/bin/ruby
require "mysql"
require "pg"

SERVER="localhost"
USERNAME="java"
PASSWORD="what?why42?"
SCHEMA="junit"

puts "Please enter the mysql root password (plain text)"
print "> "
passwd = gets.chomp

puts "--Creating MySql test framework"
#--Connect as Root
db = Mysql.real_connect(SERVER,"root",passwd)
if not db then
	puts "Could not connect to mysql database!"
	exit 1
end
puts "Connected as root. version=#{db.get_server_info}"
#(create user)
puts "   creating user"
db.query("GRANT USAGE ON *.* TO #{USERNAME}@#{SERVER}")
db.query("DROP USER #{USERNAME}@#{SERVER}")
db.query("CREATE USER #{USERNAME}@#{SERVER} IDENTIFIED BY '#{PASSWORD}'")
db.query("DROP DATABASE IF EXISTS #{SCHEMA}")
db.query("CREATE DATABASE #{SCHEMA}")
#(grant user permissions)
puts "   granting permissions"
db.query("GRANT ALL PRIVILEGES ON #{SCHEMA}.* TO #{USERNAME}@#{SERVER}")
db.close
puts "   <<closed connection"
#--Connect as Java
db = Mysql.real_connect(SERVER,USERNAME,PASSWORD,SCHEMA)
if not db then
	puts "Could not connect to mysql database as user #{USERNAME}!"
	exit 1
end
puts "Connected as #{USERNAME}. version=#{db.get_server_info}"
db.close
puts "   <<closed connection"


puts "--Creating Postgres test framework"
#--Connect as Root
puts "Creating user #{USERNAME}. (needs sudo)"
`sudo su - postgres -c "echo \\"DROP DATABASE IF EXISTS #{SCHEMA}; DROP USER IF EXISTS #{USERNAME}; CREATE USER #{USERNAME} WITH PASSWORD '#{PASSWORD}'; CREATE DATABASE #{SCHEMA}; GRANT ALL PRIVILEGES ON DATABASE #{SCHEMA} TO #{USERNAME};\\" | psql template1"`
db = PGconn.connect(SERVER,5432,'','',SCHEMA,USERNAME,PASSWORD)
puts "Connected as #{USERNAME}."
db.close
puts "   <<closed connection"

puts ""
puts "DONE (check errors on stdout, if any)"

require 'sequel'

Sequel::Model.plugin(:schema)

DB = Sequel.connect('postgres://localhost/helpfulness?user=research&password=what?why42?')

#--CUSTOM--
class Sequel::Dataset
	def finished
		self.filter(:run__finished => true)
	end
end

require 'model/task'
require 'model/run'

require 'json'

class Runs < Ramaze::Controller
	map '/'

	layout(:page){ !request.xhr? } 

	def details
		#--Variables
		rid = request[:rid]
		Ramaze::Log.debug("Fetching details for rid=#{rid}")
		#--Get Data
		#(options)
		opts = DB[:run].join(:option, :rid => :rid).filter(:run__rid => rid).select(:key,:value).order(:key).all
		#(params)
		params = DB[:run].join(:param, :rid => :rid).filter(:run__rid => rid).select(:key,:value).order(:key).all
		#(results)
		results = DB[:run].join(:global_result, :rid => :rid).filter(:run__rid => rid).select(:key,:value).order(:key).all
		
		#--Respond
		if request.xhr? and request.post?
			response['Content-Type'] = 'application/json'
			json = JSON.generate [
				{"rid" => rid,
					"name" => Run.filter(:rid => rid).first[:name]},
				{"options"=>opts, 
					"params"=>params,
					"results"=>results}, 
			]
			respond(json,200)
		elsif request.get?
			response['Content-Type'] = 'text/html'
			"Test response for rid=#{rid}"
		else
			raise "Unknown request type for 'details'"
		end
	end
end

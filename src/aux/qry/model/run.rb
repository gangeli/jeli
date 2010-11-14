class Run < Sequel::Model(:run)
	Run.subset(:foo){rid > 100}
	Run.subset(:finished){completed}
end


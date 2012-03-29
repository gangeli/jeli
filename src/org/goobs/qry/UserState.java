package org.goobs.qry;

import org.goobs.database.*;
import org.goobs.qry.DomainHandler.Axis;
import org.goobs.testing.DBResultLogger;
import org.goobs.util.Utils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;


@Table(name="viewer")
public class UserState extends DatabaseObject {


	@PrimaryKey(name="vid")
	private int id;

	@Key(name="name", length=1024)
	@Index
	private String username;


	@Key(name="min_rid")
	private int minRid = 1100; //TODO
	@Key(name="max_rid")
	private int maxRid = 1200;

	@Key(name="ignored_runparams")
	private String[] ignoredRunparams = new String[0];
	@Key(name="ignored_options")
	private String[] ignoredOptions = new String[0];
	@Key(name="ignored_results")
	private String[] ignoredResults = new String[0];

	private Iterator<DBResultLogger.Run> runIterator = null;

	private UserState(){}

	public UserState(String username){
		this.username = username;
	}


	public int getId(){ return id; }
	public String getName(){ return username; }

	public Iterable<Axis> visibleAxes(){
		//(get all options/results)
		List<String> options = options();
		List<String> results = results();
		//(forced)
		List<Axis> merged = new LinkedList<Axis>();       
		if(!Utils.contains(ignoredRunparams, "rid")){
			merged.add(new Axis("rid", "", false));
		}
		if(!Utils.contains(ignoredRunparams, "start")){
			merged.add(new Axis("start", "", false));
		}
		if(!Utils.contains(ignoredRunparams, "end")){
			merged.add(new Axis("end", "", false));
		}
		//(filter)
		for(String option : options){
			if(!Utils.contains(ignoredOptions, option)){
				merged.add(new Axis(option, "", false));
			}
		}
		for(String res : results){
			if(!Utils.contains(ignoredResults, res)){
				merged.add(new Axis(res, "", true));
			}
		}
		//(return)
//		List<Axis> toy = new LinkedList<Axis>();
//		toy.add(new Axis("rid", "", false));
//		toy.add(new Axis("gaussiansigma", "", false));
//		toy.add(new Axis("lexprior", "", false));
//		toy.add(new Axis("interpret.train.accuracy", "%", true));
//		return toy;
		return merged;
	}
	
	public HashMap<String,Object> nextRunDesc(int lastRunSeen){
		//--Ensure Iterator
		if(this.runIterator == null){
			this.runIterator = this.database.getObjectsWhere(DBResultLogger.Run.class,
					"rid >= " + this.minRid + " AND rid < " + this.maxRid + " ORDER BY rid DESC");
		}
		if(!this.runIterator.hasNext()){
			this.runIterator = null;
			return null;
		}
		//--Get Candidate
		HashMap<String,Object> runInfo = new HashMap<String, Object>();
		DBResultLogger.Run candRid = this.runIterator.next();
		while(candRid == null && this.runIterator.hasNext()){
			candRid = this.runIterator.next();
			if(candRid.rid() >= lastRunSeen){
				candRid = null;
			}
		}
		if(candRid == null){
			this.runIterator = null;
			return null;
		}
		runInfo.put("rid", candRid.rid());
		runInfo.put("start", candRid.start());
		runInfo.put("stop", candRid.stop());
		runInfo.put("name", candRid.name());
		//--Fill Options
		Iterator<DBResultLogger.Option> opts =
				this.database.getObjectsByKey(DBResultLogger.Option.class, "rid", candRid.rid());
		while(opts.hasNext()){
			DBResultLogger.Option opt = opts.next();
			try{
				runInfo.put(opt.key(), Double.parseDouble(opt.value()));
			} catch(NumberFormatException e){
				runInfo.put(opt.key(), opt.value());
			}
		}
		//--Fill Results
		Iterator<DBResultLogger.GlobalResult> results =
				this.database.getObjectsByKey(DBResultLogger.GlobalResult.class, "rid", candRid.rid());
		while(results.hasNext()){
			DBResultLogger.GlobalResult res= results.next();
			try{
				runInfo.put(res.key(), Double.parseDouble(res.value()));
			} catch(NumberFormatException e){
				runInfo.put(res.key(), res.value());
			}
		}
		//--Return
		return runInfo;
	}

	public List<String> options(){
		try{
			ResultSet rs = this.database.query("SELECT DISTINCT option.key FROM option, run WHERE option.rid = run.rid "+
					"AND run.rid >= " +this.minRid + " AND run.rid < " + this.maxRid + " ORDER BY key ASC;");
			List<String> rtn = new LinkedList<String>();
			while(rs.next()){
				rtn.add(rs.getObject(1).toString());
			}
			rs.close();
			return rtn;
		} catch(SQLException e){
			throw new DatabaseException(e);
		}
	}

	public List<String> results(){
		try{
			ResultSet rs = this.database.query("SELECT DISTINCT global_result.key FROM global_result, run WHERE global_result.rid = run.rid "+
					"AND run.rid >= " +this.minRid + " AND run.rid < " + this.maxRid + " ORDER BY key ASC;");
			List<String> rtn = new LinkedList<String>();
			while(rs.next()){
				rtn.add(rs.getObject(1).toString());
			}
			rs.close();
			return rtn;
		} catch(SQLException e){
			throw new DatabaseException(e);
		}
	}
}

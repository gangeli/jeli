package org.goobs.qry;


import org.goobs.database.Database;
import org.goobs.net.CanJSON;
import org.goobs.net.JsonHandler;
import org.goobs.net.WebServer;

import java.util.HashMap;

public class DomainHandler  extends JsonHandler{
	public static class Axis implements CanJSON {
		public final String handle;
		public final String name;
		public final String units;
		public final boolean isResult;

		public Axis(String name, String units, boolean isResult){
			this.handle = name;
			this.name = name;
			this.units = units;
			this.isResult = isResult;
		}

		@Override
		public String toJSON() {
			return JSON("name", name, "unit", units);
		}
	}

	public final Database db;
	private UserState state;

	public DomainHandler(Database db){
		this.db = db;
	}

	@Override
	public String handleJSON(HashMap<String, String> values, WebServer.HttpInfo info) {
		String idStr = values.get("view");
		if(idStr == null){
			return error("Missing call parameter 'view'");
		}
		try{
			//--Get State
			int id = Integer.parseInt(idStr);
			if(state == null){ state = db.getObjectById(UserState.class, id); }
			JSONBuilder builder = new JSONBuilder();
			//--Get Axes
			HashMap<String,Axis> mapping = new HashMap<String, Axis>();
			for(Axis a : state.visibleAxes()){
				mapping.put(a.handle, a);
			}
			//--Send JSON
			return JSON("axes", mapping);
		} catch (Exception e){
			return error(e);
		}
	}
}

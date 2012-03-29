package org.goobs.qry;

import org.goobs.database.Database;
import org.goobs.net.JsonHandler;
import org.goobs.net.WebServer;

import java.util.HashMap;

public class DataHandler extends JsonHandler {
	public final Database db;

	private UserState state = null;
	
	public DataHandler(Database db){
		this.db = db;
	}
	
	@Override
	public String handleJSON(HashMap<String, String> values, WebServer.HttpInfo info) {
		//--Arguments
		String idStr = values.get("view");
		String lastSeenStr = values.get("lastSeen");
		if(idStr == null){
			return error("Missing call parameter 'view'");
		}
		if(lastSeenStr == null){
			return error("Missing call parameter 'view'");
		}
		int lastSeen = -1;
		try{
			lastSeen = Integer.parseInt(lastSeenStr);
		} catch(NumberFormatException e){
			return error("Not a valid rid: " + lastSeenStr);
		}
		try{
			//--Get State
			int id = Integer.parseInt(idStr);
			if(state == null){ state = db.getObjectById(UserState.class, id); }
			//--Get Data
			HashMap<String,Object> runInfo = state.nextRunDesc(lastSeen);
			return JSON("data", runInfo);
		} catch (Exception e){
			return error(e);
		}
	}
}

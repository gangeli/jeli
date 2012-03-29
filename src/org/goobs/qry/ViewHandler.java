package org.goobs.qry;

import org.goobs.database.Database;
import org.goobs.net.JsonHandler;
import org.goobs.net.WebServer;

import java.util.HashMap;

/**
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public class ViewHandler extends JsonHandler {
	public final Database db;

	public ViewHandler(Database db){
		this.db = db;
		if(db.hasTable(UserState.class)){
			db.dropTable(UserState.class);
		}
		if(!db.hasTable(UserState.class)){
			db.emptyObject(UserState.class, "scratch").flush();
		}
	}

	@Override
	public String handleJSON(HashMap<String, String> values, WebServer.HttpInfo info) {
		String username = values.get("name");
		if(username == null){
			return JSON("id", -1);
		} else {
			UserState state = db.getObjectByKey(UserState.class, "name", username);
			if(state == null){
				String shouldCreateString = values.get("create");
				if("true".equalsIgnoreCase(shouldCreateString)){
					state = db.emptyObject(UserState.class,username).flush();
				} else {
					return JSON("id", -1);
				}
			}
			return JSON("id", state.getId());
		}
	}
}

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import lotus.domino.Base;
import lotus.domino.Database;
import lotus.domino.View;
import lotus.domino.Document;
import lotus.domino.NotesException;
import net.prominic.gja_v084.JavaServerAddinGenesis;

public class ViewsHelper extends JavaServerAddinGenesis {
	private String m_filePath = "viewshelper.nsf";
	EventViews m_event = null;
	
	public ViewsHelper(String[] args) {
		super();
		m_filePath = args[0];
	}

	public ViewsHelper() {
		super();
	}

	@Override
	protected String getJavaAddinVersion() {
		return "1.0.0";
	}
	
	@Override
	protected String getJavaAddinDate() {
		return "2023-11-21 21:30";
	}
	
	protected boolean runNotesAfterInitialize() {
		try {
			Database database = m_session.getDatabase(null, m_filePath);
			if (database == null || !database.isOpen()) {
				logSevere("(!) LOAD FAILED - database not found: " + m_filePath);
				return false;
			}
			
			m_event = new EventViews("Views", 1, true, this.m_logger);
			m_event.session = this.m_session;
			m_event.events = getView();
			eventsAdd(m_event);
		} catch (Exception e) {
			logSevere(e);
			return false;
		}
		return true;
	}
	
	@Override
	protected void runNotesBeforeListen() {
		m_event.triggerHelperOnStart();
	}
	
	protected boolean resolveMessageQueueState(String cmd) {
		boolean flag = super.resolveMessageQueueState(cmd);
		if (flag)
			return true;

		if (cmd.startsWith("update")) {
			m_event.events = getView();
			logMessage("update - completed");
		} else if (cmd.startsWith("trigger")) {
			m_event.triggerFireForce();
			logMessage("trigger - completed");
		} else {
			logMessage("invalid command (use -h or help to get details)");
		}

		return true;
	}
	
	private List<HashMap<String, Object>> getView() {
		List<HashMap<String, Object>> list = null;
		
		try {
			Database database = m_session.getDatabase(null, m_filePath);
			if (database == null || !database.isOpen()) {
				logMessage("(!) LOAD FAILED - database not found: " + m_filePath);
				return null;
			}

			list = new ArrayList<HashMap<String, Object>>();

			View view = database.getView("Views");
			Document doc = view.getFirstDocument();
			while (doc != null) {
				Document docNext = view.getNextDocument(doc);

				// start new thread for each agent
				String title = doc.getItemValueString("Title");
				String json = doc.getItemValueString("JSON");

				JSONObject obj = getJSONObject(json);
				if (obj != null) {
					String server = (String) obj.get("server");		// required
					String filePath = (String) obj.get("database");	// required
					String viewName = (String) obj.get("view");		// required
					Long interval = (Long) obj.get("interval");		// required
					boolean runIfModified = obj.containsKey("runIfModified") && (Boolean) obj.get("runIfModified");
					boolean runOnStart = obj.containsKey("runOnStart") && (Boolean) obj.get("runOnStart");
					
					HashMap<String, Object> event = new HashMap<String, Object>();
					event.put("title", title);
					event.put("server", server);
					event.put("filePath", filePath);
					event.put("view", viewName);
					event.put("interval", interval);
					event.put("runIfModified", runIfModified);
					event.put("runOnStart", runOnStart);
					event.put("lastRun", new Date());

					list.add(event);
				}
				else {
					logMessage(title + ": invalid json");
				}

				recycle(doc);
				doc = docNext;
			}

			recycle(view);
			recycle(database);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return list;
	}

	protected void showHelp() {
		logMessage("*** Usage ***");
		logMessage("load runjava " + this.getJavaAddinName() + " <agentshelper.nsf>");
		logMessage("tell " + this.getJavaAddinName() + " <command>");
		logMessage("   quit             Unload addin");
		logMessage("   help             Show help information (or -h)");
		logMessage("   info             Show version");
		logMessage("   trigger          Fire all agents from " + m_filePath);
		logMessage("   update           Update config from " + m_filePath);

		int year = Calendar.getInstance().get(Calendar.YEAR);
		logMessage("Copyright (C) Prominic.NET, Inc. 2023" + (year > 2023 ? " - " + Integer.toString(year) : ""));
		logMessage("See https://prominic.net for more details.");
	}

	/**
	 * JSONObject
	 */
	private JSONObject getJSONObject(String json) {
		JSONParser parser = new JSONParser();
		try {
			return (JSONObject) parser.parse(json);
		} catch (ParseException e) {
			return null;
		}
	}

	/**
	 * Display run configuration
	 */
	protected void showInfoExt() {
		logMessage("config       " + m_filePath);
		logMessage("events       " + m_event.events.size());
	}

	/**
	 * Recycle Domino objects.
	 */
	private static void recycle(Base object) throws NotesException {
		if (object == null)
			return;
		object.recycle();
		object = null;
	}

}
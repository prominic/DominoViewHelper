import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

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
		return "1.0.3";
	}

	@Override
	protected String getJavaAddinDate() {
		return "2024-03-05 18:00";
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
			m_event.events = getViews();
			eventsAdd(m_event);
		} catch (Exception e) {
			logSevere(e);
			return false;
		}
		return true;
	}

	protected boolean resolveMessageQueueState(String cmd) {
		boolean flag = super.resolveMessageQueueState(cmd);
		if (flag)
			return true;

		if (cmd.startsWith("update")) {
			m_event.events = getViews();
			logMessage("update - completed");
		} else if (cmd.startsWith("trigger")) {
			m_event.triggerFireForce();
			logMessage("trigger - completed");
		} else {
			logMessage("invalid command (use -h or help to get details)");
		}

		return true;
	}

	private List<HashMap<String, Object>> getViews() {
		List<HashMap<String, Object>> list = null;

		try {
			Database database = m_session.getDatabase(null, m_filePath);
			if (database == null || !database.isOpen()) {
				logMessage("(!) LOAD FAILED - database not found: " + m_filePath);
				return null;
			}

			list = new ArrayList<HashMap<String, Object>>();

			View view = database.getView("($views)");
			Document doc = view.getFirstDocument();
			while (doc != null) {
				Document docNext = view.getNextDocument(doc);

				// start new thread for each agent
				String title = doc.getItemValueString("Title");

				String server = doc.getItemValueString("Server");
				String filePath = doc.getItemValueString("Database");
				@SuppressWarnings("unchecked")
				Vector<String> views = doc.getItemValue("Views");
				long interval = doc.getItemValueInteger("interval");
				boolean runIfModified = doc.getItemValueString("runIfModified").equals("1");
				String log = doc.getItemValueString("Log");
				
				HashMap<String, Object> event = new HashMap<String, Object>();
				event.put("title", title);
				event.put("server", server);
				event.put("filePath", filePath);
				event.put("views", views);
				event.put("interval", interval);
				event.put("runIfModified", runIfModified);
				event.put("lastRun", new Date());
				event.put("log", log);
				
				list.add(event);

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
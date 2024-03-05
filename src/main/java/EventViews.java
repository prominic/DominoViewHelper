import java.util.Date;

import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import lotus.domino.Database;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;
import net.prominic.gja_v084.Event;
import net.prominic.gja_v084.GLogger;

public class EventViews extends Event {
	public Session session = null;
	public List<HashMap<String, Object>> events = null;

	public EventViews(String name, long seconds, boolean fireOnStart, GLogger logger) {
		super(name, seconds, fireOnStart, logger);
	}

	@Override
	public void run() {
		for (int i = 0; i < events.size(); i++) {
			HashMap<String, Object> event = events.get(i);

			refreshView(event);	
		}
	}

	public void triggerFireForce() {
		for (int i = 0; i < events.size(); i++) {
			HashMap<String, Object> event = events.get(i);
			refreshView(event);
		}
	}

	private void refreshView(HashMap<String, Object> event) {
		try {
			boolean runIfModified = (Boolean) event.get("runIfModified");
			Date lastRun = (Date) event.get("lastRun");
			Long interval = (Long) event.get("interval");
			String server = (String) event.get("server");
			String filePath = (String) event.get("filePath");
			Database database = session.getDatabase(server, filePath);
			String log = (String) event.get("log");

			if (database == null || !database.isOpen()) {
				String err = String.format("%s !! %s not found", server, filePath);
				logMessage(log, err, true);
				return;
			}

			@SuppressWarnings("unchecked")
			Vector<String> views = (Vector<String>) event.get("views");
			
			for (int i = 0; i<views.size(); i++) {
				String viewName = views.get(i);
				View view = database.getView(viewName);

				if (view == null) {
					database.recycle();
					String err = String.format("%s view not found in database %s", viewName, filePath);
					logMessage(log, err, true);
					return;
				}

				boolean updated = false;
				if (runIfModified) {
					if (database.getLastModified().toJavaDate().compareTo(lastRun) > 0) {
						event.put("lastRun", new Date());
						updated = true;
						String message = database.getTitle() + " - modified";
						logMessage(log, message, false);
					}
				}
				
				if (!updated && interval > 0) {
					Date now = new Date();
					long seconds = (now.getTime()-lastRun.getTime())/1000;
					if (seconds >= interval) {
						updated = true;
						String message = database.getTitle() + " - interval";
						logMessage(log, message, false);
					};
				}

				if (updated) {
					String message = view.getName() + ": refresh!";
					logMessage(log, message, false);
					view.refresh();
					event.put("lastRun", new Date());
				}

				view.recycle();

			}

			database.recycle();
		} catch (NotesException e) {
			e.printStackTrace();
		}
	}

	private void logMessage(String logOpt, String message, boolean severe) {
		if (logOpt.equals("1") || logOpt.equals("2")) {
			if (severe) {
				getLogger().severe(message);				
			}
			else {
				getLogger().info(message);				
			}
		}
		if (logOpt.equals("2")) {
			if (severe) {
				System.err.print(message);
			}
			else {
				System.out.print(message);
			}
		}
	}
}

/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.event.consumers;

import org.apache.log4j.Logger;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.curate.QueueManager;
import org.dspace.event.Consumer;
import org.dspace.event.Event;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;

import java.sql.SQLException;
import java.util.LinkedList;

/**
 * Abstract event consumer that queues curation tasks when specific events occur.
 *
 * @author Andrea Schweer schweer@waikato.ac.nz for the LCoNZ IRRs
 * @author Kim Shepherd kim@shepherd.nz update and refactor for DSpace 6.x generic usage
 */
public abstract class QueueTaskOnEvent implements Consumer {

	ConfigurationService configurationService;
	private static final Logger log = Logger.getLogger(QueueTaskOnEvent.class);

	private QueueManager queueManager;

	@Override
	public void initialize() throws Exception {
		configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
		queueManager = new QueueManager();
		queueManager.initTaskNames(getTasksProperty());
		if (!queueManager.hasTaskNames()) {
			log.error("QueueTaskOnEvent: no configuration value found for tasks to queue (" + getTasksProperty() + "), can't initialise.");
			return;
		}

		queueManager.initQueueName(getQueueProperty());
	}

	@Override
	public void consume(Context ctx, Event event) throws Exception {
		Item item = null;
		if (isApplicableEvent(ctx, event)) {
			// check whether it is the last applicable event in the queue
			LinkedList<Event> events = ctx.getEvents();
			for (Event queuedEvent : events) {
				if (isApplicableEvent(ctx, queuedEvent)) {
					return;
				}
			}
			item = findItem(ctx, event);
		}

		if (item == null) {
			// not applicable -> skip
			return;
		}

		queueManager.addToQueue(item);
	}

	abstract Item findItem(Context ctx, Event event) throws SQLException;

	abstract boolean isApplicableEvent(Context ctx, Event event) throws SQLException;

	public void end(Context ctx) throws Exception {
		queueManager.queueForCuration(ctx);
	}

	public void finish(Context ctx) throws Exception {
	}

	abstract String getTasksProperty();

	abstract String getQueueProperty();
}

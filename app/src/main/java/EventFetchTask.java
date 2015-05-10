/**
 * Created by amoln on 5/7/2015.
 */
package com.example.calendarquickstart;

import android.os.AsyncTask;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.util.DateTime;

import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.google.api.services.calendar.model.Event;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * An asynchronous task that handles the Calendar API event list retrieval.
 * Placing the API calls in their own task ensures the UI stays responsive.
 */
public class EventFetchTask extends AsyncTask<Void, Void, Void> {
    private com.example.calendarquickstart.UpcomingEventsActivity mActivity;

    /**
     * Constructor.
     * @param activity UpcomingEventsActivity that spawned this task.
     */
    EventFetchTask(UpcomingEventsActivity activity) {
        this.mActivity = activity;
    }

    /**
     * Background task to call Calendar API to fetch event list.
     * @param params no parameters needed for this task.
     */
    @Override
    protected Void doInBackground(Void... params) {
        try {
            mActivity.clearEvents();
            mActivity.updateEventList(fetchEventsFromCalendar());

        } catch (final GooglePlayServicesAvailabilityIOException availabilityException) {
            mActivity.showGooglePlayServicesAvailabilityErrorDialog(
                    availabilityException.getConnectionStatusCode());

        } catch (UserRecoverableAuthIOException userRecoverableException) {
            mActivity.startActivityForResult(
                    userRecoverableException.getIntent(),
                    UpcomingEventsActivity.REQUEST_AUTHORIZATION);

        } catch (IOException e) {
            mActivity.updateStatus("The following error occurred: " +
                    e.getMessage());
        }
        return null;
    }

    /**
     * Fetch a list of the next 10 events from the primary calendar.
     * @return List of Strings describing returned events.
     * @throws IOException
     */
    private List<String> fetchEventsFromCalendar() throws IOException {
        // List the next 10 events from the primary calendar.
        DateTime now = new DateTime(System.currentTimeMillis());
        List<String> eventStrings = new ArrayList<String>();
        Events events = mActivity.mService.events().list("primary")
                .setMaxResults(10)
                .setTimeMin(now)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute();
        List<Event> items = events.getItems();

        for (Event event : items) {
            DateTime start = event.getStart().getDateTime();
            if (start == null) {
                // All-day events don't have start times, so just use
                // the start date.
                start = event.getStart().getDate();
            }
            eventStrings.add(
                    String.format("%s (%s) %s", event.getSummary(), start, event.getId()));
        }
        return eventStrings;
    }
/*
    public boolean CreateMeetingNotesPage(String meetingTitle)
    {
            String[] meetingDetails = meetingTitle.split(",");
            Integer eventId = Integer.parseInt(meetingDetails[2]);

            Event eventToUpdate = mService.Events.Get("primary", eventId);
            eventToUpdate.setDescription(eventToUpdate.getDescription() + "\n" + "http://www.onenote.com/Notebooks?auth=1");

            DateTime st = eventToUpdate.getOriginalStartTime().getDateTime();
            Event reminder = new Event();
            reminder.setSummary("Take Notes With OneNote");

            DateTime start = eventToUpdate.getOriginalStartTime().getDateTime();
            reminder.setStart(new EventDateTime().setDateTime(start));
            DateTime end = eventToUpdate.getOriginalEndTime().getDateTime();
            reminder.setEnd(new EventDateTime().setDateTime(end));

// Insert the new event
            Event createdEvent = mActivity.mService.Events.Insert("primary", event).execute();

            return true;
    }*/
}

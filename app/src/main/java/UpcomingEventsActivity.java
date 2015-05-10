/**
 * Created by amoln on 5/7/2015.
 */
package com.example.calendarquickstart;

import com.example.calendarquickstart.EventFetchTask;
import com.google.android.gms.common.ConnectionResult;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UpcomingEventsActivity extends Activity {
    /**
     * A Calendar service object used to query or modify calendars via the
     * Calendar API. Note: Do not confuse this class with the
     * com.google.api.services.calendar.model.Calendar class.
     */
    com.google.api.services.calendar.Calendar mService;

    GoogleAccountCredential credential;
    private TextView mStatusText;
    private ListView mListView;
    private TextView mEventText;
    private Button mButton;
    final HttpTransport transport = AndroidHttp.newCompatibleTransport();
    final JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = {CalendarScopes.CALENDAR};

    /**
     * Create the main activity.
     * @param savedInstanceState previously saved instance data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout activityLayout = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        activityLayout.setLayoutParams(lp);
        activityLayout.setOrientation(LinearLayout.VERTICAL);
        activityLayout.setPadding(16, 16, 16, 16);

        ViewGroup.LayoutParams tlp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        mStatusText = new TextView(this);
        mStatusText.setLayoutParams(tlp);
        mStatusText.setTypeface(null, Typeface.BOLD);
        mStatusText.setText("Retrieving events...");
        activityLayout.addView(mStatusText);

        mEventText = new TextView(this);
        mEventText.setLayoutParams(tlp);
        mEventText.setPadding(16, 16, 16, 16);
        mEventText.setVerticalScrollBarEnabled(true);
        mEventText.setMovementMethod(new ScrollingMovementMethod());
        activityLayout.addView(mEventText);

        mListView = new ListView(this);
        mListView.setLayoutParams(tlp);
        mListView.setPadding(16, 16, 16, 16);
        mListView.setVerticalScrollBarEnabled(true);
        mListView.setSelection(1);
        activityLayout.addView(mListView);

        mButton = new Button(this);
        mButton.setLayoutParams(tlp);
        mButton.setPadding(16, 16, 16, 16);
        activityLayout.addView(mButton);

        //mListView.setMovementMethod(new ScrollingMovementMethod());

        setContentView(activityLayout);

        // Initialize credentials and calendar service.
        SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
        credential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff())
                .setSelectedAccountName(settings.getString(PREF_ACCOUNT_NAME, null));

        mService = new com.google.api.services.calendar.Calendar.Builder(
                transport, jsonFactory, credential)
                .setApplicationName("Calendar API Android Quickstart")
                .build();
    }

    /**
     * Called whenever this activity is pushed to the foreground, such as after
     * a call to onCreate().
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (isGooglePlayServicesAvailable()) {
            refreshEventList();
        } else {
            mStatusText.setText("Google Play Services required: " +
                    "after installing, close and relaunch this app.");
        }
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode code indicating the result of the incoming
     *     activity result.
     * @param data Intent (containing result data) returned by incoming
     *     activity result.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode == RESULT_OK) {
                    refreshEventList();
                } else {
                    isGooglePlayServicesAvailable();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        credential.setSelectedAccountName(accountName);
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.commit();
                        refreshEventList();
                    }
                } else if (resultCode == RESULT_CANCELED) {
                    mStatusText.setText("Account unspecified.");
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    refreshEventList();
                } else {
                    chooseAccount();
                }
                break;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Attempt to get a list of calendar events to display. If the email
     * address isn't known yet, then call chooseAccount() method so the user
     * can pick an account.
     */
    private void refreshEventList() {
        if (credential.getSelectedAccountName() == null) {
            chooseAccount();
        } else {
            if (isDeviceOnline()) {
                new EventFetchTask(this).execute();
            } else {
                mStatusText.setText("No network connection available.");
            }
        }
    }

    /**
     * Clear any existing events from the list display and update the header
     * message; called from background threads and async tasks that need to
     * update the UI (in the UI thread).
     */
    public void clearEvents() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStatusText.setText("Retrieving events");
                mEventText.setText("");
            }
        });
    }

    /**
     * Fill the event display with the given List of strings; called from
     * background threads and async tasks that need to update the UI (in the
     * UI thread).
     * @param eventStrings a List of Strings to populate the event display with.
     */
    public void updateEventList(final List<String> eventStrings) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (eventStrings == null) {
                    mStatusText.setText("Error retrieving events!");
                } else if (eventStrings.size() == 0) {
                    mStatusText.setText("No upcoming events found.");
                } else {
                    mStatusText.setText("Your upcoming events: ");
                    /*
                    String[] listItems = new String[]{};
                    eventStrings.toArray(listItems);
                    ListAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, listItems);*/
                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(getBaseContext(), android.R.layout.simple_list_item_multiple_choice, eventStrings);
                    ListAdapter listAdapter = adapter;
                    //ListView mListView = (ListView)findViewById(R.id.listView);
                    mListView.setAdapter(listAdapter);
                    mButton.setText("Add Notes and Reminder");
                    //mEventText.setText(TextUtils.join("\n\n", eventStrings));
                    mListView.setOnItemClickListener(
                            new AdapterView.OnItemClickListener() {
                                @Override
                                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                                    mListView.setSelection(position);
/*
                                    String meetingTitle = mListView.getItemAtPosition(0).toString();
                                    String[] meetingDetails = meetingTitle.split(",");
                                    Integer eventId = Integer.parseInt(meetingDetails[2]);

                                    Event eventToUpdate = mService.Events.Get("primary", eventId.toString());
                                    eventToUpdate.setDescription(eventToUpdate.getDescription() + "\n" + "http://www.onenote.com/Notebooks?auth=1");

                                    DateTime st = eventToUpdate.getOriginalStartTime().getDateTime();
                                    Event reminder = new Event();
                                    reminder.setSummary("Take Notes With OneNote");

                                    DateTime start = eventToUpdate.getOriginalStartTime().getDateTime();
                                    reminder.setStart(new EventDateTime().setDateTime(start));
                                    //DateTime end = eventToUpdate.getOrigetOriginalEndTime().getDateTime();
                                    //reminder.setEnd(new EventDateTime().setDateTime(end));

// Insert the new event
                                    Event createdEvent = mService.Events.Insert("primary", event).execute();

                                    //return true;*/
                                    CreateMeetingNotesPage(mListView.getItemAtPosition(0).toString());
                                }
                            }
                    );


                }
            }
        });
    }
    /**
     * Show a status message in the list header TextView; called from background
     * threads and async tasks that need to update the UI (in the UI thread).
     * @param message a String to display in the UI header TextView.
     */
    public void updateStatus(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStatusText.setText(message);
            }
        });
    }

    /**
     * Starts an activity in Google Play Services so the user can pick an
     * account.
     */
    private void chooseAccount() {
        startActivityForResult(
                credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
    }

    /**
     * Checks whether the device currently has a network connection.
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date. Will
     * launch an error dialog for the user to update Google Play Services if
     * possible.
     * @return true if Google Play Services is available and up to
     *     date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        final int connectionStatusCode =
                GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (GooglePlayServicesUtil.isUserRecoverableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
            return false;
        } else if (connectionStatusCode != ConnectionResult.SUCCESS ) {
            return false;
        }
        return true;
    }

    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     * @param connectionStatusCode code describing the presence (or lack of)
     *     Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Dialog dialog = GooglePlayServicesUtil.getErrorDialog(
                        connectionStatusCode,
                        UpcomingEventsActivity.this,
                        REQUEST_GOOGLE_PLAY_SERVICES);
                dialog.show();
            }
        });
    }
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
    }

/*
    private boolean UpdateTheMeetingInvite()
    {
        return true;
    }

    private boolean AddReminderToTakeNotes()
    {
        return true;
    }*/
}

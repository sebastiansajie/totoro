package com.totoro.incardisplay;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.facebook.widget.ProfilePictureView;

/**
 *  Fragment shown once a user opens the scoreboard
 */
public class ScoreboardFragment extends Fragment {
	private boolean stop = false;
	private double globalD = 0;
	public static BlockingQueue<Double> queue = new ArrayBlockingQueue<Double>(100);

	// Tag used when logging messages
	private static final String TAG = ScoreboardFragment.class.getSimpleName();

	// Store the Application (as you can't always get to it when you can't access the Activity - e.g. during rotations)
	private OmniDriveApplication application;

	// LinearLayout as the container for the scoreboard entries
	private LinearLayout scoreboardContainer;
	private final Double nullVal = -1.0;

	// FrameLayout of the progress container to show the spinner
	private FrameLayout progressContainer;

	// Handler for putting messages on Main UI thread from background thread after fetching the scores
	private Handler uiHandler;
	private Handler scoreHandler;
	private Handler boardHandler;

	private TelnetClientOutput tco;
	private Random rgen = new Random();

	private boolean simulate = true;

	private long numberIterations = 0;
	private final int NUM_UPDATE = 10;

	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		application = (OmniDriveApplication) getActivity().getApplication();
		if (simulate) {
			try {
				tco = new TelnetClientOutput();
				tco.execute();
			} catch (Exception e) {
				System.out.println("EXCEPTION THROWN");
				// TODO Auto-generated catch block
				e.printStackTrace(); 
			}
		} 
		// Instantiate the handler
		uiHandler = new Handler();
		scoreHandler = new Handler();
		boardHandler = new Handler();
		setRetainInstance(true);
		long startTime = System.currentTimeMillis();

		scoreHandler.removeCallbacks(updateScoreTask);
		scoreHandler.postDelayed(updateScoreTask, 100);

	}




	private Runnable updateScoreTask = new Runnable() {
		public void run() {
			TextView yourScore = (TextView) getView().findViewById(R.id.current_score);
			double d = 0.0;
			if (simulate) {
				try {
					d = TelnetClientOutput.queue.take();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				String currentUserFBID = application.getCurrentFBUser().getId();
				HttpClient client = new DefaultHttpClient();
				String getURL = "http://omnidrive.herokuapp.com/getscore?fbid=" + currentUserFBID;
				//fbid: blah hiscore: blah
				HttpGet get = new HttpGet(getURL);
				try {
					HttpResponse responseGet = client.execute(get);
					HttpEntity responseEntity = responseGet.getEntity();
					String response = EntityUtils.toString(responseEntity);
					if (!response.equals(null)) {
						JSONObject currUser;
						try {
							currUser = new JSONObject(response);
							String fetchedScoreAsString = currUser.optString("highscore");
							if (fetchedScoreAsString != null) {
								d = Double.parseDouble(fetchedScoreAsString);
							}

						} catch (JSONException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				} catch (ClientProtocolException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			DecimalFormat df = new DecimalFormat("####0.0");
			yourScore.setText("" + df.format(d));
			globalD = Double.parseDouble(df.format(d));
			if(++numberIterations % NUM_UPDATE == 0)	{
				TextView recommendation = (TextView) getView().findViewById(R.id.recommendation);
				recommendation.setText(getRecommendation());
			}
			scoreHandler.postDelayed(updateScoreTask, 1000);
		}
	};

	private String getRecommendation()	{
		switch((int)(5 * Math.random()))	{
		case 0: return "Try to avoid flooring the accelerator.\nSudden changes in acceleration produce significantly larger\n quantities of carbon dioxide.";
		case 1: return "Try hitting the brake pedal more softly.\nThis will prevent degradation of your brakes.";
		case 2: return "Try to turn more smoothly.";
		case 3: return "Avoid accelerating or braking while on inclines.\nUse your momentum to carry you through inclines.";
		case 4: return "Avoid idling your engine.\nTurn off your car if you're going to not use it for extended periods of time.";
		default:
			return "No recommendation.";
		}
	}

	@TargetApi(13)
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup parent,
			Bundle savedInstanceState) {

		View v = inflater.inflate(R.layout.fragment_scoreboard, parent, false);

		scoreboardContainer = (LinearLayout)v.findViewById(R.id.scoreboardContainer);
		progressContainer = (FrameLayout)v.findViewById(R.id.progressContainer);

		// Set the progressContainer as invisible by default
		progressContainer.setVisibility(View.INVISIBLE);

		// Note: Scoreboard is populated during onResume below

		Button pause_button = (Button) v.findViewById(R.id.pause);

		pause_button.setOnClickListener(new View.OnClickListener(){
			public void onClick(View v) {
				try {
					queue.put(nullVal);
					scoreHandler.removeCallbacks(updateScoreTask);
					//boardHandler.removeCallbacks(f);
				} catch (Exception e) {

				}
			}
		});	

		return v;
	}

	public double getQueue() {
		try {
			double d = queue.take();
			return d;
		} catch (InterruptedException e) {
			e.printStackTrace();
			return nullVal;
		}
	}

	public void putQueue(double d) {
		try {
			queue.put(d);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	// Close the game and show the specified error to the user
	private void closeAndShowError(String error) {
		Bundle bundle = new Bundle();
		bundle.putString("error", error);

		Intent i = new Intent();
		i.putExtras(bundle);

		getActivity().setResult(Activity.RESULT_CANCELED, i);
		getActivity().finish();
	}

	@Override
	public void onResume() {
		super.onResume();

		// Populate scoreboard - fetch information if necessary ...
		/*if (application.getScoreboardEntriesList() == null) {
			// scoreboardEntriesList is null, so fetch the information from Facebook (scoreboard will be updated in
			// the scoreboardEntriesFetched callback) and show the progress spinner while doing so
			progressContainer.setVisibility(View.VISIBLE);
			fetchScoreboardEntries();
		} else {
			// Information has already been fetched, so populate the scoreboard
			populateScoreboard();
		}*/

		if (application.getScoreboardEntriesList() == null) {
			//progressContainer.setVisibility(View.VISIBLE);
		}

		progressContainer.setVisibility(View.VISIBLE);
		//boardHandler.removeCallbacks(f);
		//boardHandler.postDelayed(f, 0);
		fetchScoreboardFunc();
	}

	private Runnable f = new Runnable()  {
		// Fetch the scores ...
		//AsyncTask.execute(new Runnable() {
		public void run() {
			fetchScoreboardFunc();
		}
	};

	private void fetchScoreboardFunc() {
		// Fetch the scores ...
		AsyncTask.execute(new Runnable() {
			public void run() {
				try {
					// Instantiate the scoreboardEntriesList
					ArrayList<ScoreboardEntry> scoreboardEntriesList = new ArrayList<ScoreboardEntry>();

					// Get the attributes used for the HTTP GET
					String currentUserFBID = application.getCurrentFBUser().getId();
					//String currentUserAccessToken = Session.getActiveSession().getAccessToken();

					// Execute the HTTP Get to our server for the scores of the user's friends
					HttpClient client = new DefaultHttpClient();
					/* Update this */
					String getURL = "http://omnidrive.herokuapp.com/data?fbid=" + currentUserFBID + "&data=" + globalD;
					HttpGet get = new HttpGet(getURL);
					HttpResponse responseGet = client.execute(get);

					// Parse the response
					HttpEntity responseEntity = responseGet.getEntity();
					String response = EntityUtils.toString(responseEntity);
					if (!response.equals(null)) {
						JSONObject jObject = new JSONObject(response);
						JSONArray responseJSONArray = jObject.getJSONArray("friends");
						//JSONArray responseJSONArray = new JSONArray(response);

						// Go through the response JSON Array to populate the scoreboard
						if (responseJSONArray != null && responseJSONArray.length() > 0) {

							// Loop through all users that have been retrieved
							for (int i=0; i<responseJSONArray.length(); i++) {
								// Store the user details in the following attributes
								String userID = null;
								String userName = null;
								double userScore = -1.0;

								// Extract the user information
								JSONObject currentUser = responseJSONArray.optJSONObject(i);
								if (currentUser != null) {
									userID = currentUser.optString("fbid");
									userName = currentUser.optString("first_name");
									String fetchedScoreAsString = currentUser.optString("highscore");
									if (fetchedScoreAsString != null) {
										userScore = Double.parseDouble(fetchedScoreAsString);

										/*double nextR = rgen.nextDouble();
										boolean nextB = rgen.nextBoolean();
										if (nextB) {
											userScore *= nextR;
										} else {
											nextR += 0.001;
											userScore /= nextR;
										}*/
									}
									if (userID != null && userName != null && userScore >= 0) {
										// All attributes have been successfully fetched, so create a new
										// ScoreboardEntry and add it to the List
										ScoreboardEntry currentUserScoreboardEntry =
												new ScoreboardEntry(userID, userName, userScore);
										scoreboardEntriesList.add(currentUserScoreboardEntry);
									}
								}
							}
						}
					}

					// Now that all scores should have been fetched and added to the scoreboardEntriesList, sort it,
					// set it within scoreboardFragment and then callback to scoreboardFragment to populate the scoreboard
					Comparator<ScoreboardEntry> comparator = Collections.reverseOrder();
					Collections.sort(scoreboardEntriesList, comparator);
					application.setScoreboardEntriesList(scoreboardEntriesList);

					// Populate the scoreboard on the UI thread
					uiHandler.post(new Runnable() {
						@Override
						public void run() {
							populateScoreboard();
						}
					});
					boardHandler.postDelayed(f, 2500);

				} catch (Exception e) {
					Log.e(OmniDriveApplication.TAG, e.toString());
					closeAndShowError(getResources().getString(R.string.network_error));
				}
			}

		});
	}

	private void populateScoreboard() {
		// Ensure all components are firstly removed from scoreboardContainer
		scoreboardContainer.removeAllViews();

		// Ensure the progress spinner is hidden
		progressContainer.setVisibility(View.INVISIBLE);

		// Ensure scoreboardEntriesList is not null and not empty first
		if (application.getScoreboardEntriesList() == null || application.getScoreboardEntriesList().size() <= 0) {
			closeAndShowError(getResources().getString(R.string.error_no_scores));
		} else {
			// Iterate through scoreboardEntriesList, creating new UI elements for each entry
			int index = 0;
			Iterator<ScoreboardEntry> scoreboardEntriesIterator = application.getScoreboardEntriesList().iterator();
			while (scoreboardEntriesIterator.hasNext()) {
				// Get the current scoreboard entry
				final ScoreboardEntry currentScoreboardEntry = scoreboardEntriesIterator.next();

				// FrameLayout Container for the currentScoreboardEntry ...

				// Create and add a new FrameLayout to display the details of this entry
				FrameLayout frameLayout = new FrameLayout(getActivity());
				scoreboardContainer.addView(frameLayout);

				// Set the attributes for this frameLayout
				int topPadding = getResources().getDimensionPixelSize(R.dimen.scoreboard_entry_top_margin);
				frameLayout.setPadding(0, topPadding, 0, 0);

				// ImageView background image ...
				{
					// Create and add an ImageView for the background image to this entry
					ImageView backgroundImageView = new ImageView(getActivity());
					frameLayout.addView(backgroundImageView);

					// Set the image of the backgroundImageView
					String uri = "drawable/scores_stub_even";
					if (index % 2 != 0) {
						// Odd entry
						uri = "drawable/scores_stub_odd";
					}
					int imageResource = getResources().getIdentifier(uri, null, getActivity().getPackageName());
					Drawable image = getResources().getDrawable(imageResource);
					backgroundImageView.setImageDrawable(image);

					// Other attributes of backgroundImageView to modify
					FrameLayout.LayoutParams backgroundImageViewLayoutParams = new FrameLayout.LayoutParams(
							FrameLayout.LayoutParams.WRAP_CONTENT,
							FrameLayout.LayoutParams.WRAP_CONTENT);
					int backgroundImageViewMarginTop = getResources().getDimensionPixelSize(R.dimen.scoreboard_background_imageview_margin_top);
					backgroundImageViewLayoutParams.setMargins(0, backgroundImageViewMarginTop, 0, 0);
					backgroundImageViewLayoutParams.gravity = Gravity.LEFT;
					if (index % 2 != 0) {
						// Odd entry
						backgroundImageViewLayoutParams.gravity = Gravity.RIGHT;
					}
					backgroundImageView.setLayoutParams(backgroundImageViewLayoutParams);
				}

				// ProfilePictureView of the current user ...
				{
					// Create and add a ProfilePictureView for the current user entry's profile picture
					ProfilePictureView profilePictureView = new ProfilePictureView(getActivity());
					frameLayout.addView(profilePictureView);

					// Set the attributes of the profilePictureView
					int profilePictureViewWidth = getResources().getDimensionPixelSize(R.dimen.scoreboard_profile_picture_view_width);
					FrameLayout.LayoutParams profilePictureViewLayoutParams = new FrameLayout.LayoutParams(profilePictureViewWidth, profilePictureViewWidth);
					int profilePictureViewMarginLeft = 0;
					int profilePictureViewMarginTop = getResources().getDimensionPixelSize(R.dimen.scoreboard_profile_picture_view_margin_top);
					int profilePictureViewMarginRight = 0;
					int profilePictureViewMarginBottom = 0;
					if (index % 2 == 0) {
						profilePictureViewMarginLeft = getResources().getDimensionPixelSize(R.dimen.scoreboard_profile_picture_view_margin_left);
					} else {
						profilePictureViewMarginRight = getResources().getDimensionPixelSize(R.dimen.scoreboard_profile_picture_view_margin_right);
					}
					profilePictureViewLayoutParams.setMargins(profilePictureViewMarginLeft, profilePictureViewMarginTop,
							profilePictureViewMarginRight, profilePictureViewMarginBottom);
					profilePictureViewLayoutParams.gravity = Gravity.LEFT;
					if (index % 2 != 0) {
						// Odd entry
						profilePictureViewLayoutParams.gravity = Gravity.RIGHT;
					}
					profilePictureView.setLayoutParams(profilePictureViewLayoutParams);

					// Finally set the id of the user to show their profile pic
					profilePictureView.setProfileId(currentScoreboardEntry.getId());
				}

				// LinearLayout to hold the text in this entry

				// Create and add a LinearLayout to hold the TextViews
				LinearLayout textViewsLinearLayout = new LinearLayout(getActivity());
				frameLayout.addView(textViewsLinearLayout);

				// Set the attributes for this textViewsLinearLayout
				FrameLayout.LayoutParams textViewsLinearLayoutLayoutParams = new FrameLayout.LayoutParams(
						FrameLayout.LayoutParams.WRAP_CONTENT,
						FrameLayout.LayoutParams.WRAP_CONTENT);
				int textViewsLinearLayoutMarginLeft = 0;
				int textViewsLinearLayoutMarginTop = getResources().getDimensionPixelSize(R.dimen.scoreboard_textviews_linearlayout_margin_top);
				int textViewsLinearLayoutMarginRight = 0;
				int textViewsLinearLayoutMarginBottom = 0;
				if (index % 2 == 0) {
					textViewsLinearLayoutMarginLeft = getResources().getDimensionPixelSize(R.dimen.scoreboard_textviews_linearlayout_margin_left);
				} else {
					textViewsLinearLayoutMarginRight = getResources().getDimensionPixelSize(R.dimen.scoreboard_textviews_linearlayout_margin_right);
				}
				textViewsLinearLayoutLayoutParams.setMargins(textViewsLinearLayoutMarginLeft, textViewsLinearLayoutMarginTop,
						textViewsLinearLayoutMarginRight, textViewsLinearLayoutMarginBottom);
				textViewsLinearLayoutLayoutParams.gravity = Gravity.LEFT;
				if (index % 2 != 0) {
					// Odd entry
					textViewsLinearLayoutLayoutParams.gravity = Gravity.RIGHT;
				}
				textViewsLinearLayout.setLayoutParams(textViewsLinearLayoutLayoutParams);
				textViewsLinearLayout.setOrientation(LinearLayout.VERTICAL);

				// TextView with the position and name of the current user
				{
					// Set the text that should go in this TextView first
					int position = index+1;
					String currentScoreboardEntryTitle = position + ". " + currentScoreboardEntry.getName();

					// Create and add a TextView for the current user position and first name
					TextView titleTextView = new TextView(getActivity());
					textViewsLinearLayout.addView(titleTextView);

					// Set the text and other attributes for this TextView
					titleTextView.setText(currentScoreboardEntryTitle);
					titleTextView.setTextAppearance(getActivity(), R.style.ScoreboardPlayerNameFont);
				}

				// TextView with the score of the current user
				{
					// Create and add a TextView for the current user score
					TextView scoreTextView = new TextView(getActivity());
					textViewsLinearLayout.addView(scoreTextView);

					// Set the text and other attributes for this TextView
					scoreTextView.setText("Score: " + currentScoreboardEntry.getScore());
					scoreTextView.setTextAppearance(getActivity(), R.style.ScoreboardPlayerScoreFont);
				}

				// Finally make this frameLayout clickable so that a game starts with the user smashing
				// the user represented by this frameLayout in the scoreContainer
				frameLayout.setOnTouchListener(new OnTouchListener() {

					@Override
					public boolean onTouch(View v, MotionEvent event) {
						if (event.getAction() == MotionEvent.ACTION_UP) {
							Bundle bundle = new Bundle();
							bundle.putString("user_id", currentScoreboardEntry.getId());

							Intent i = new Intent();
							i.putExtras(bundle);

							getActivity().setResult(Activity.RESULT_FIRST_USER , i);
							getActivity().finish();
							return false;
						} else {
							return true;
						}
					}

				});

				// Increment the index before looping back
				index++;
			}
		}
	}
}

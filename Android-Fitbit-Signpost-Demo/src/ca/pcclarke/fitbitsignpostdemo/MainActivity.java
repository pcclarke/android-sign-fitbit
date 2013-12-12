package ca.pcclarke.fitbitsignpostdemo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import oauth.signpost.OAuth;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.basic.DefaultOAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;

public class MainActivity extends Activity {

	private static final String TAG = "FitbitSignpostDemo";
	private static final String OAUTH_KEY = "YOUR_OAUTH_KEY"; // Put your Consumer key here
	private static final String OAUTH_SECRET = "YOUR_OAUTH_SECRET"; // Put your Consumer secret here
	private static final String OAUTH_CALLBACK_SCHEME = "demo"; // Arbitrary, but make sure this matches the scheme in the manifest
	private static final String OAUTH_CALLBACK_URL = OAUTH_CALLBACK_SCHEME + "://callback";
	
	private OAuthConsumer mConsumer;
	private OAuthProvider mProvider;
	private SharedPreferences prefs;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mConsumer = new CommonsHttpOAuthConsumer(OAUTH_KEY, OAUTH_SECRET);
		mProvider = new DefaultOAuthProvider(
				"http://api.fitbit.com/oauth/request_token",
				"http://api.fitbit.com/oauth/access_token",
				"http://www.fitbit.com/oauth/authorize");
		
		// Read the preferences to see if we have tokens
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		String token = prefs.getString("token", null);
		String tokenSecret = prefs.getString("tokenSecret", null);
		if (token != null && tokenSecret != null) {
			mConsumer.setTokenWithSecret(token, tokenSecret); // We have tokens, use them
		}
	}
	
	// Check if this is a callback from OAuth
	@Override
	public void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		Log.d(TAG, "intent: " + intent);

		Uri uri = intent.getData();
		if (uri != null && uri.getScheme().equals(OAUTH_CALLBACK_SCHEME)) {
			Log.d(TAG, "callback: " + uri.getPath());

			String verifier = uri.getQueryParameter(OAuth.OAUTH_VERIFIER);
			Log.d(TAG, "verifier: " + verifier);

			new RetrieveAccessTokenTask(this).execute(verifier);
		}
	}
	
	// Action on clicking the authorize button
	public void onClickAuthorize(View view) {
		new OAuthAuthorizeTask().execute();
	}
	
	// Action on clicking the request button
	public void onClickRequest(View view) {
		if(mConsumer.getToken() != null) {
			String requestUrl = "http://api.fitbit.com/1/user/-/profile.json";
			new MakeRequest().execute(requestUrl);
		} else {
			Toast.makeText(MainActivity.this, "You must authorize before making requests", Toast.LENGTH_LONG).show();
		}
	}
	
	// Responsible for starting the FitBit authorization
	class OAuthAuthorizeTask extends AsyncTask<Void, Void, String> {
		
		@Override
		protected String doInBackground(Void... params) {
			String authUrl;
			String message = null;
			try {
				authUrl = mProvider.retrieveRequestToken(mConsumer, OAUTH_CALLBACK_URL);
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl));
				startActivity(intent);
			} catch (OAuthMessageSignerException e) {
				message = "OAuthMessageSignerException";
				e.printStackTrace();
			} catch (OAuthNotAuthorizedException e) {
				message = "OAuthNotAuthorizedException";
				e.printStackTrace();
			} catch (OAuthExpectationFailedException e) {
				message = "OAuthExpectationFailedException";
				e.printStackTrace();
			} catch (OAuthCommunicationException e) {
				message = "OAuthCommunicationException";
				e.printStackTrace();
			}

			return message;
		}

		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
			if (result != null) {
				Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
			}
		}
	}
	
	// Responsible for retrieving access tokens from FitBit on callback
	class RetrieveAccessTokenTask extends AsyncTask<String, Void, String> {

		public MainActivity myActivity = null;
		
		public RetrieveAccessTokenTask(MainActivity myActivity) {
			this.myActivity = myActivity;
		}
		
		@Override
		protected String doInBackground(String... params) {
			String message = null;
			String verifier = params[0];
			try {
				// Get the token
				Log.d(TAG, "mConsumer: " + mConsumer);
				Log.d(TAG, "mProvider: " + mProvider);
				
				mProvider.retrieveAccessToken(mConsumer, verifier);
				String token = mConsumer.getToken();
				String tokenSecret = mConsumer.getTokenSecret();
				mConsumer.setTokenWithSecret(token, tokenSecret);

				Log.d(TAG, String.format("verifier: %s, token: %s, tokenSecret: %s", verifier, token, tokenSecret));

				// Store token in preferences
				prefs.edit().putString("token", token)
						.putString("tokenSecret", tokenSecret).commit();

				Log.d(TAG, "token: " + token);

			} catch (OAuthMessageSignerException e) {
				message = "OAuthMessageSignerException";
				e.printStackTrace();
			} catch (OAuthNotAuthorizedException e) {
				message = "OAuthNotAuthorizedException";
				e.printStackTrace();
			} catch (OAuthExpectationFailedException e) {
				message = "OAuthExpectationFailedException";
				e.printStackTrace();
			} catch (OAuthCommunicationException e) {
				message = "OAuthCommunicationException";
				e.printStackTrace();
			}
			return message;
		}

		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
			if (result != null) {
				Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
			}
		}
	}
	
	// Request Fitbit data from API
	class MakeRequest extends AsyncTask<String, Void, String[]> {

		@Override
		protected String[] doInBackground(String... params) {
			String message = null;
			String[] responseXml = new String[params.length];

			for (int i = 0; i < params.length; i++) {
				String requestUrl = params[i];
				responseXml[i] = null;
				
				DefaultHttpClient httpclient = new DefaultHttpClient();
				HttpGet request = new HttpGet(requestUrl);
				try {
					mConsumer.sign(request);
					HttpResponse response = httpclient.execute(request);
					Log.i(TAG, "Statusline : " + response.getStatusLine());
					InputStream data = response.getEntity().getContent();
					BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(data));
					String responseLine;
					StringBuilder responseBuilder = new StringBuilder();
					
					while ((responseLine = bufferedReader.readLine()) != null) {
						responseBuilder.append(responseLine);
					}
					responseXml[i] = responseBuilder.toString();
	
				} catch (OAuthMessageSignerException e) {
					message = "OAuthMessageSignerException";
					e.printStackTrace();
				} catch (OAuthExpectationFailedException e) {
					message = "OAuthExpectationFailedException";
					e.printStackTrace();
				} catch (OAuthCommunicationException e) {
					message = "OAuthCommunicationException";
					e.printStackTrace();
				} catch (ClientProtocolException e) {
					message = "ClientProtocolException";
					e.printStackTrace();
				} catch (IOException e) {
					message = "IOException";
					e.printStackTrace();
				}
			}
			return responseXml;
		}

		@Override
		protected void onPostExecute(String[] result) {
			super.onPostExecute(result);
			Log.d(TAG, result[0]);
			TextView displayRequest = (TextView) findViewById(R.id.requestTextView);
			displayRequest.setText(result[0]);
		}
	}
}

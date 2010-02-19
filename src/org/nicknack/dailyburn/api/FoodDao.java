package org.nicknack.dailyburn.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;

import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.nicknack.dailyburn.DailyBurnDroid;
import org.nicknack.dailyburn.model.Food;
import org.nicknack.dailyburn.model.FoodLogEntries;
import org.nicknack.dailyburn.model.FoodLogEntry;
import org.nicknack.dailyburn.model.Foods;
import org.nicknack.dailyburn.model.NilClasses;

import android.util.Log;

import com.thoughtworks.xstream.XStream;

public class FoodDao {

	private CommonsHttpOAuthConsumer consumer;
	DefaultHttpClient client;
	private XStream xstream;

	public FoodDao(DefaultHttpClient client, CommonsHttpOAuthConsumer consumer) {

		HttpParams parameters = new BasicHttpParams();
		SchemeRegistry schemeRegistry = new SchemeRegistry();
		SSLSocketFactory sslSocketFactory = SSLSocketFactory.getSocketFactory();
		sslSocketFactory
				.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
		schemeRegistry.register(new Scheme("https", sslSocketFactory, 443));
		ClientConnectionManager manager = new ThreadSafeClientConnManager(
				parameters, schemeRegistry);
		this.client = new DefaultHttpClient(manager, parameters);

		this.consumer = consumer;
		configureXStream();
	}

	private void configureXStream() {
		xstream = new XStream();
		xstream.alias("foods", Foods.class);
		xstream.addImplicitCollection(Foods.class, "foods");
		xstream.alias("food", Food.class);
		xstream.registerConverter(new FoodConverter());

		xstream.alias("food-log-entries", FoodLogEntries.class);
		xstream.addImplicitCollection(FoodLogEntries.class, "entries");
		xstream.alias("food-log-entry", FoodLogEntry.class);
		xstream.registerConverter(new FoodLogEntryConverter());

		xstream.alias("nil-classes", NilClasses.class);

	}

	public List<Food> getFavoriteFoods() {
		Foods foods = null;
		try {
			HttpGet request = new HttpGet(
					"https://dailyburn.com/api/foods/favorites.xml");
			consumer.sign(request);
			HttpResponse response = client.execute(request);
			foods = (Foods) xstream.fromXML(response.getEntity().getContent());
		} catch (Exception e) {
			Log.e(DailyBurnDroid.TAG, e.getMessage());
			e.printStackTrace();
		}
		return foods.foods;
	}

	public List<Food> search(String param) {
		return search(param, String.valueOf(1));
	}

	public List<Food> search(String param, String pageNum) {

		Foods foods = null;
		try {
			String encodedParam = URLEncoder.encode(param, "UTF-8");
			encodedParam += "&per_page=10";
			encodedParam += "&page=" + pageNum;
			HttpGet request = new HttpGet(
					"https://dailyburn.com/api/foods/search.xml?input="
							+ encodedParam);
			consumer.sign(request);
			HttpResponse response = client.execute(request);

			// //USE TO PRINT TO LogCat (Make a filter on dailyburndroid tag)
			// BufferedReader in = new BufferedReader(new
			// InputStreamReader(connection.getInputStream()));
			// String line = null;
			// while((line = in.readLine()) != null) {
			// Log.d("dailyburndroid",line);
			// }
			foods = (Foods) xstream.fromXML(response.getEntity().getContent());

			Log.d(DailyBurnDroid.TAG, foods.foods.get(0).getName() + " "
					+ foods.foods.get(0).getBrand());
			Log.d(DailyBurnDroid.TAG, "T_Url: "
					+ foods.foods.get(0).getThumbUrl());
			Log.d(DailyBurnDroid.TAG, "N_Url: "
					+ foods.foods.get(0).getNormalUrl());
		} catch (Exception e) {
			Log.d(DailyBurnDroid.TAG, e.getMessage());
		}
		return foods.foods;
	}

	public String getNutritionLabel(int foodId) {
		BufferedReader in = null;
		String fixedHtml = null;
		try {
			String encodedParam = URLEncoder.encode((new Integer(foodId))
					.toString(), "UTF-8");
			HttpGet request = new HttpGet("https://dailyburn.com/api/foods/"
					+ encodedParam + "/nutrition_label");
			consumer.sign(request);
			HttpResponse response = client.execute(request);
			in = new BufferedReader(new InputStreamReader(response.getEntity()
					.getContent()));

			StringBuilder sb = new StringBuilder();

			String inputLine;
			while ((inputLine = in.readLine()) != null) {
				sb.append(inputLine).append('\n');
				Log.d("dailyburndroid", inputLine);
			}

			String html = sb.toString();
			int len = html.length();
			// The following snippet is needed to make the html safe
			// for the data:// uri which is passed to WebView
			StringBuilder buf = new StringBuilder(len + 100);
			for (int i = 0; i < len; i++) {
				char chr = html.charAt(i);
				switch (chr) {
				case '%':
					buf.append("%25");
					break;
				case '\'':
					buf.append("%27");
					break;
				case '#':
					buf.append("%23");
					break;
				default:
					buf.append(chr);
				}
				fixedHtml = buf.toString();
			}
		} catch (Exception e) {
			Log.e(DailyBurnDroid.TAG, e.getMessage());
			e.printStackTrace();
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		return fixedHtml;
	}

	public void addFavoriteFood(int id) throws OAuthMessageSignerException,
			OAuthExpectationFailedException, OAuthNotAuthorizedException,
			ClientProtocolException, IOException, OAuthCommunicationException {
		// create a request that requires authentication
		HttpPost post = new HttpPost(
				"https://dailyburn.com/api/foods/add_favorite");
		final List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		// 'status' here is the update value you collect from UI
		nvps.add(new BasicNameValuePair("id", String.valueOf(id)));
		post.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
		// set this to avoid 417 error (Expectation Failed)
		post.getParams().setBooleanParameter(
				CoreProtocolPNames.USE_EXPECT_CONTINUE, false);
		// sign the request
		consumer.sign(post);
		// send the request
		final HttpResponse response = client.execute(post);
		// response status should be 200 OK
		int statusCode = response.getStatusLine().getStatusCode();
		final String reason = response.getStatusLine().getReasonPhrase();
		// release connection
		response.getEntity().consumeContent();
		if (statusCode != 200) {
			Log.e(DailyBurnDroid.TAG, reason);
			throw new OAuthNotAuthorizedException();
		}
	}

	public void deleteFavoriteFood(int id) throws OAuthMessageSignerException,
			OAuthExpectationFailedException, OAuthNotAuthorizedException,
			ClientProtocolException, IOException, OAuthCommunicationException {
		// create a request that requires authentication
		HttpPost post = new HttpPost(
				"https://dailyburn.com/api/foods/delete_favorite");
		final List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		// 'status' here is the update value you collect from UI
		nvps.add(new BasicNameValuePair("id", String.valueOf(id)));
		post.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
		// set this to avoid 417 error (Expectation Failed)
		post.getParams().setBooleanParameter(
				CoreProtocolPNames.USE_EXPECT_CONTINUE, false);
		// sign the request
		consumer.sign(post);
		// send the request
		final HttpResponse response = client.execute(post);
		// response status should be 200 OK
		int statusCode = response.getStatusLine().getStatusCode();
		final String reason = response.getStatusLine().getReasonPhrase();
		// release connection
		response.getEntity().consumeContent();
		if (statusCode != 200) {
			Log.e(DailyBurnDroid.TAG, reason);
			throw new OAuthNotAuthorizedException();
		}
	}

	public void deleteFoodLogEntry(int entryId) throws OAuthMessageSignerException, OAuthExpectationFailedException, ClientProtocolException, IOException, OAuthCommunicationException {
		String param = String.valueOf(entryId);
		HttpDelete delete = new HttpDelete("https://dailyburn.com/api/food_log_entries?id=" + param);
		consumer.sign(delete);
		HttpResponse response = client.execute(delete);
		int statusCode = response.getStatusLine().getStatusCode();
		final String reason = response.getStatusLine().getReasonPhrase();
		if(statusCode != 200) {
			Log.e(DailyBurnDroid.TAG, reason);
		}
	}
	
	public void addFoodLogEntry(int foodId, String servings_eaten, int year,
			int monthOfYear, int dayOfMonth)
			throws OAuthMessageSignerException,
			OAuthExpectationFailedException, ClientProtocolException,
			IOException, OAuthNotAuthorizedException, OAuthCommunicationException {
		// create a request that requires authentication
		HttpPost post = new HttpPost(
				"https://dailyburn.com/api/food_log_entries");
		final List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		// 'status' here is the update value you collect from UI
		nvps.add(new BasicNameValuePair("food_log_entry[food_id]", String.valueOf(foodId)));
		nvps.add(new BasicNameValuePair("food_log_entry[servings_eaten]", servings_eaten));
		GregorianCalendar cal = new GregorianCalendar(year, monthOfYear,
				dayOfMonth);
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		String formattedDate = format.format(cal.getTime());
		nvps.add(new BasicNameValuePair("food_log_entry[logged_on]", formattedDate));
		post.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
		// set this to avoid 417 error (Expectation Failed)
		post.getParams().setBooleanParameter(
				CoreProtocolPNames.USE_EXPECT_CONTINUE, false);
		// sign the request
		consumer.sign(post);
		// send the request
		final HttpResponse response = client.execute(post);
		// response status should be 200 OK
		int statusCode = response.getStatusLine().getStatusCode();
		final String reason = response.getStatusLine().getReasonPhrase();
		// release connection
		response.getEntity().consumeContent();
		if (statusCode != 200) {
			Log.e(DailyBurnDroid.TAG, reason);
			throw new OAuthNotAuthorizedException();
		}
	}

	public List<FoodLogEntry> getFoodLogEntries() {
		return getFoodLogEntries(0, 0, 0);
	}

	public List<FoodLogEntry> getFoodLogEntries(int year, int monthOfYear,
			int dayOfMonth) {
		FoodLogEntries entries = null;
		try {
			HttpGet request = null;
			if (year != 0 && monthOfYear != 0 && dayOfMonth != 0) {
				GregorianCalendar cal = new GregorianCalendar(year,
						monthOfYear, dayOfMonth);
				SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
				String formattedDate = format.format(cal.getTime());
				String dateParam = "?date=" + formattedDate;
				request = new HttpGet(
						"https://dailyburn.com/api/food_log_entries.xml"
								+ dateParam);
			} else {
				request = new HttpGet(
						"https://dailyburn.com/api/food_log_entries.xml");
			}
			consumer.sign(request);
			HttpResponse response = client.execute(request);
			// //USE TO PRINT TO LogCat (Make a filter on dailyburndroid tag)
			// BufferedReader in = new BufferedReader(new
			// InputStreamReader(response.getEntity().getContent()));
			// String line = null;
			// while((line = in.readLine()) != null) {
			// Log.d("dailyburndroid",line);
			// }
			Object result = xstream.fromXML(response.getEntity().getContent());
			if (result instanceof NilClasses) {
				return new ArrayList<FoodLogEntry>();
			} else {
				entries = (FoodLogEntries) result;
				// entries = (List<FoodLogEntry>)
				// xstream.fromXML(response.getEntity().getContent());
			}
		} catch (Exception e) {
			Log.e(DailyBurnDroid.TAG, e.getMessage());
			e.printStackTrace();
		} 
		return entries.entries;
	}

}

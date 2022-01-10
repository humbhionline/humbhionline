package in.succinct.mandi.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.routing.Config;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Class OCRUtil. Created during ondc hackathon !!
 *
 * @author karthik
 */
public class OCRUtils {
	static Logger logger = Logger.getLogger(OCRUtils.class.getName());

	/**
	 * Parses a png image as a data url
	 * @param url
	 * @return
	 */

	public static String parseImage(String url, MimeType mimeType) {
		CloseableHttpClient httpClient = HttpClients.createDefault();
		HttpPost httpPost = new HttpPost("https://api.ocr.space/parse/image");
		httpPost.addHeader("apikey",  Config.instance().getProperty("ocr.space.api.key"));

		// Request parameters and other properties.
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("base64Image", url));
		params.add(new BasicNameValuePair("language", "eng"));
		params.add(new BasicNameValuePair( "filetype", mimeType.getDefaultFileExtension().toUpperCase()));

		params.add(new BasicNameValuePair("detectOrientation", "true"));
		params.add(new BasicNameValuePair("scale", "true"));

		params.add(new BasicNameValuePair("OCREngine", "1"));
		try {
			httpPost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			logger.log(Level.SEVERE, "Error on HTTP post", e);
		}

		try {
			HttpResponse response = httpClient.execute(httpPost);
			HttpEntity respEntity = response.getEntity();

			if (respEntity != null) {
				String responseJson = EntityUtils.toString(respEntity);
				return parseResponse(responseJson);
			}
		} catch (ClientProtocolException e) {
			logger.log(Level.SEVERE, "Error on HTTP post", e);
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Error on HTTP post", e);
		}
		return null;
	}

	/**
	 * Parses the content.
	 *
	 * @param content the content
	 * @return the string
	 */
	private static String parseResponse(String content) {
		if (content != null && !content.isEmpty()) {
			JsonObject jsonObject = new JsonParser().parse(content).getAsJsonObject();
			if (jsonObject.get("OCRExitCode").getAsInt() == 1) {
//				{  //response sample
//					  "ParsedResults": [
//					    {
//					      "TextOverlay": {
//					        "Lines": [],
//					        "HasOverlay": false,
//					        "Message": "No overlay requested."
//					      },
//					      "TextOrientation": "0",
//					      "FileParseExitCode": 1,
//					      "ParsedText": "Masájhai-licious\nbalcednoedle\nLETS CODK\nSWEETCORN\nodles\nMSide U99ested oarnehing\nTion\nprtionis oi\n60g B R\nan average & yoar ol chd",
//					      "ErrorMessage": "",
//					      "ErrorDetails": ""
//					    }
//					  ],
//					  "OCRExitCode": 1,
//					  "IsErroredOnProcessing": false,
//					  "ProcessingTimeInMilliseconds": "7078"
//					}
				return jsonObject.get("ParsedResults").getAsJsonArray().get(0).getAsJsonObject().get("ParsedText")
						.getAsString();
			}
		}
		return null;
	}


}

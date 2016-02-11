package equitybankgroup.secugenplugin;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;


/**
 * Created by Mathew.Godia on 4/15/14.
 */
public class ServerConnect {
    HttpURLConnection conn;
    BufferedReader br;
    InputStream is;
    OutputStream os;

    public String processRequest(String serverUrl, JSONObject params) throws IOException {

        String resp = "{'response_code':404,'response_message':'Server Unreachable'}";
        try {

            //Proxy Settings
            URL url = new URL(serverUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(60000);//Milliseconds
            conn.setConnectTimeout(15000);//Milliseconds
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(params.toString().length());

            //Set Http Header
            conn.setRequestProperty("Content-Type", "application/json;charset=utf-8");
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");

            //starts the query - Open
            conn.connect();
            //Send Parameters
            os = new BufferedOutputStream(conn.getOutputStream());
            os.write(params.toString().getBytes());

            //Clean up
            os.flush();

            //Get Response
            int response = conn.getResponseCode();//200 = Success
            Log.d("AccountOpening", "Response Code: " + response);
            if(response == 200){
                is = conn.getInputStream();
                //Convert the InputStream into a String
                resp = readResponse(is);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                is.close();
            }
            if (os != null) {
                os.close();
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
        return resp;
    }

    public String readResponse(InputStream is) throws IOException {
        try {
            Reader reader = new InputStreamReader(is, "UTF-8");
            br = new BufferedReader(reader);
            String resp = br.readLine();
            JSONObject json = new JSONObject(resp);
            return json.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                br.close();
            }
        }
        return "{'response_code':401,'response_message':'Unexpected_Error'}";
    }
}

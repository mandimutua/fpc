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
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.Key;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;


/**
 * Created by Mathew.Godia on 4/15/14.
 */
public class SecureServerConnect {
    HttpsURLConnection conn;
    BufferedReader br;
    InputStream is;
    OutputStream os;

    public String processRequest(String serverUrl, JSONObject params) throws IOException, NoSuchAlgorithmException {

        String resp = "{'response_code':404,'response_message':'Server Unreachable'}";
//        create a TrustManager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {

                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
//                return new X509Certificate[0];
                        return null;
                    }
                }
        };
        //Install the all-trusting trust manager
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            //Host Name Verifier
            HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            //Proxy Settings
            URL url = new URL(serverUrl);
            conn = (HttpsURLConnection) url.openConnection();
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
            if (response == 200) {
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

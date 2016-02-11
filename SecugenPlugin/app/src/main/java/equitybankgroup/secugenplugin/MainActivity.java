package equitybankgroup.secugenplugin;

import android.os.AsyncTask;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class MainActivity extends ActionBarActivity {
    private FingerPrintController fpc;
    String serverUrl = "";
    String serverPrints;
    private int REQUEST_CODE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initDevice();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void initDevice() {
        try {
//            fpc = new FingerPrintController(this.cordova.getActivity().getApplicationContext());
            fpc = new FingerPrintController(this);
            fpc.initDevice();
        } catch (Exception e) {
            System.err.println("SecuginPlugin.initDevice();; Error: " + e.getMessage());
        }

    }

//        public String capturePrint(JSONArray args){
//        System.err.println("Capturing Print");
//        try{
//            JSONObject res = fpc.registerPrint();
//            return res.toString();
//        }catch(Exception e){
//            System.err.println("Exception: "+e.getMessage());
//            return "Error: capturePrint();;"+e.getMessage();
//        }
//    }

    public void capturePrint(View v) {
        JSONObject params = new JSONObject();
        try {
            switch (v.getId()) {
                case R.id.button:
                    REQUEST_CODE = 100;
                    String payload = registerPrint();
                    params.put("payload", payload);
                    serverUrl = "http://10.1.9.100:7001/AccountOpeningAPI/savePrint";
                    new DoServerTask(params).execute(serverUrl);
                    break;
                case R.id.button2:
                    REQUEST_CODE = 200;
                    verifyPrint();
                    break;
            }
        } catch (Exception e) {
            Log.d("AccountOpening", e.getMessage());
        }
    }

    public String registerPrint() {
        System.err.println("Capturing Print");
        try {

            ImageView iv = (ImageView) findViewById(R.id.imageView1);
//            JSONObject res = fpc.registerPrint(iv);
            JSONObject res = fpc.generateTemplate(iv);
            return res.toString();
        } catch (Exception e) {
            System.err.println("Exception: " + e.getMessage());
            return "Error: capturePrint();;" + e.getMessage();
        }
    }

    public String verifyPrint() {
        System.err.println("Capturing Print");
        JSONObject res = new JSONObject();
        try {
            getServerPrints();
            ImageView iv = (ImageView) findViewById(R.id.imageView2);

            if (serverPrints.isEmpty()) {
                res.put("response_code", "0");
                res.put("response_message", "Failed To Get Verify With Server");
                Log.d("AccountOpening","No Prints");
            } else {
                res = fpc.verify(iv, serverPrints);
            }
        } catch (Exception e) {
            System.err.println("Exception: " + e.getMessage());
            return "Error: capturePrint();;" + e.getMessage();
        }

        return res.toString();
    }

    public void getServerPrints() {
        try {
            JSONObject params = new JSONObject();
            params.put("action", "getPrints");
            serverUrl = "http://10.1.9.100:7001/AccountOpeningAPI/getPrints";
            new DoServerTask(params).execute(serverUrl);
        } catch (Exception e) {
            System.err.println("Exception: " + e.getMessage());
//            return "Error: capturePrint();;" + e.getMessage();
        }
    }

    private class DoServerTask extends AsyncTask<String, Void, String> {
        private JSONObject params;

        public DoServerTask(JSONObject params) {
            this.params = params;
            Log.d("AccountOpening",params.toString());
        }

        @Override
        protected void onPreExecute() {
            setSupportProgressBarIndeterminateVisibility(true);
        }


        @Override
        protected String doInBackground(String... urls) {
            try {
                if (serverUrl.contains("https")) {
                    Log.d("AccountOpening", "SecureServerConnect Called");
                    SecureServerConnect sc = new SecureServerConnect();
                    return sc.processRequest(urls[0], params);
                } else {
                    Log.d("AccountOpening", "ServerConnect Called");
                    ServerConnect sc = new ServerConnect();
                    return sc.processRequest(urls[0], params);
                }
            } catch (Exception e) {
                Log.d("AccountOpening", e.toString());
                e.printStackTrace();
                return "{'response_code':'400','message':'errorServerUnreachable'}";
            }
        }

        @Override
        protected void onPostExecute(String result) {
            setSupportProgressBarIndeterminateVisibility(false);
            try {
                JSONObject res = new JSONObject(result);
                Log.d("AccountOpening", "Response Code: " + res.get("response_code") + "\nResponse Message: " + res.get("response_message").toString());
                if (res.getString("response_code").equalsIgnoreCase("1")) {
                    if (REQUEST_CODE == 100) {
                        Toast.makeText(MainActivity.this, res.getString("response_message"), Toast.LENGTH_LONG).show();
                    } else if (REQUEST_CODE == 200) {
                        serverPrints = res.get("response_payload").toString();
                    }
                } else {
                    Toast.makeText(MainActivity.this, res.getString("response_message"), Toast.LENGTH_LONG).show();
                }
            } catch (JSONException e) {
                Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        }
    }

}

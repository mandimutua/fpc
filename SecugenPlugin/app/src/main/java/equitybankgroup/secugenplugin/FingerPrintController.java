package equitybankgroup.secugenplugin;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import com.lynx.bio.EQBio;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import SecuGen.FDxSDKPro.JSGFPLib;
import SecuGen.FDxSDKPro.SGAutoOnEventNotifier;
import SecuGen.FDxSDKPro.SGDeviceInfoParam;
import SecuGen.FDxSDKPro.SGFDxDeviceName;
import SecuGen.FDxSDKPro.SGFDxErrorCode;
import SecuGen.FDxSDKPro.SGFDxSecurityLevel;
import SecuGen.FDxSDKPro.SGFDxTemplateFormat;
import SecuGen.FDxSDKPro.SGFingerInfo;
import SecuGen.FDxSDKPro.SGFingerPresentEvent;

/**
 * Created by tiberius on 11/20/14.
 */
public class FingerPrintController implements SGFingerPresentEvent {
    private static final String TAG = "SECUGEN PLUGIN";
    private byte[] mRegisterImage;
    private byte[] mRegisterTemplate;
    private byte[] mVerifyImage;
    private byte[] mVerifyTemplate;
    private int mImageWidth;
    private int mImageHeight;
    private int[] mMaxTemplateSize;
    private int[] grayBuffer;


    private Bitmap grayBitmap;
    private boolean mLed;
    private boolean mAutoOnEnabled;

    private JSGFPLib sgfpLib;
    private SGAutoOnEventNotifier autoOn;
    private static final String ACTION_USB_PERMISSION = "com.equitybankgroup.secugenplugin.USB_PERMISSION";
    private PendingIntent mPermissionIntent;
    private IntentFilter filter;
    private Context context;
    private UsbManager manager;


    public FingerPrintController(Context context) {
        this.context = context;
        debugMessage("Initializing Class");
    }

    private void debugMessage(String message) {
        // Log.d(TAG, message);
        System.err.println(TAG + " " + message);
    }

    public void SGFingerPresentCallback() {
        autoOn.stop();
//        fingerDetectedHandler.sendMessage(new Message());
    }


    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            debugMessage("Vendor ID: " + device.getVendorId() + "\n");
                            debugMessage("Product ID: " + device.getProductId() + "\n");
                        } else {

                            debugMessage(TAG + "mUsbReceiver.onReceive() Device is null");
                        }

                    } else {
                        debugMessage(TAG + "mUsbReceiver.onReceive() permission denied for service " + device);
                    }
                }
            }
        }
    };

    public SGDeviceInfoParam getDeviceInfo() {
        //Get Device Info
        SGDeviceInfoParam deviceInfo = new SGDeviceInfoParam();
        if (sgfpLib.GetDeviceInfo(deviceInfo) != SGFDxErrorCode.SGFDX_ERROR_NONE) {
            Log.d(TAG, "Unable To Get Device Info");
            Toast.makeText(context, "Unable To Get Device Info", Toast.LENGTH_LONG).show();
        }
        return deviceInfo;
    }

    public void initDevice() {
        try {
//Get Usb Manager from Android
            manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

            sgfpLib = new JSGFPLib((UsbManager) context.getSystemService(Context.USB_SERVICE));
            debugMessage("jnisgfplib version: " + sgfpLib.Version() + "\n");
            mLed = false;
            long err = sgfpLib.Init(SGFDxDeviceName.SG_DEV_AUTO);
            if (err != SGFDxErrorCode.SGFDX_ERROR_NONE) {
                if (err == SGFDxErrorCode.SGFDX_ERROR_DEVICE_NOT_FOUND) {
                    String message = "Either a fingerprint device is not attached or the attached fingerprint device is not supported.";
                    debugMessage(message);
//                callbackContext.error(message);
                } else {
                    String message = "Fingerprint device initialization failed!";
                    debugMessage(message);
//                callbackContext.error(message);
                }
            } else {
                UsbDevice usbDevice = sgfpLib.GetUsbDevice();
                if (usbDevice == null) {
                    String message = "SDU04P or SDU03P fingerprint sensor not found!";
                    debugMessage(message);
//                callbackContext.error(message);
                }
                //USB Permissions
                mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
                filter = new IntentFilter();
                filter.addAction(ACTION_USB_PERMISSION);
                context.registerReceiver(mUsbReceiver, filter);
                manager.requestPermission(usbDevice, mPermissionIntent);
                debugMessage("jnisgfplib version: " + sgfpLib.Version() + "\n");

            }
//        autoOn = new SGAutoOnEventNotifier(sgfpLib, this);
//        mAutoOnEnabled = false;

        } catch (Exception e) {
            debugMessage("initDevice() Error");
            debugMessage("Error: " + e.getMessage());
        }

    }

    //This message handler is used to access local resources not accessible by SGFingerPresentCallback()
    //because it is called by a separate thread
//    public Handler fingerDetectedHandler = new Handler(){
//        public void handleMessage(Message msg){
//            CaptureFingerPrint();
//            if(mAutoOnEnabled){
//                EnableControls();
//            }
//        }
//    };


//    public JSONObject registerPrint(ImageView mImageViewFingerprint) {
    public JSONObject registerPrint() {
        JSONObject res = new JSONObject();
        try {
            //Init Device
            long openError = sgfpLib.OpenDevice(0);
            debugMessage("Open Device Response: " + openError);

            //Get Device Info
            SGDeviceInfoParam deviceInfo = getDeviceInfo();
            mImageWidth = deviceInfo.imageWidth;
            mImageHeight = deviceInfo.imageHeight;

            //Initialize Bitmap Image
            grayBuffer = new int[JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES * JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES];
            for (int i = 0; i < grayBuffer.length; ++i)
                grayBuffer[i] = android.graphics.Color.GRAY;
            grayBitmap = Bitmap.createBitmap(JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES, JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES, Bitmap.Config.ARGB_8888);
            grayBitmap.setPixels(grayBuffer, 0, JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES, 0, 0, JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES, JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES);
//            mImageViewFingerprint.setImageBitmap(grayBitmap);

            if (mRegisterImage != null) {
                mRegisterImage = null;
            }

            //Capture Print
            mMaxTemplateSize = new int[1];
            sgfpLib.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400);
            sgfpLib.GetMaxTemplateSize(mMaxTemplateSize);

            long dwTimeStart = 0, dwTimeEnd = 0, dwTimeElapsed = 0;
            debugMessage("Clicked REGISTER\n");
            mRegisterTemplate = new byte[mMaxTemplateSize[0]];
            mRegisterImage = new byte[mImageWidth * mImageHeight];
            ByteBuffer byteBuf = ByteBuffer.allocate(mImageWidth * mImageHeight);
            dwTimeStart = System.currentTimeMillis();
            long result = sgfpLib.GetImage(mRegisterImage);
            dwTimeEnd = System.currentTimeMillis();
            dwTimeElapsed = dwTimeEnd - dwTimeStart;
            debugMessage("GetImage() ret: " + result + "[" + dwTimeElapsed + " ms]\n");
            Bitmap b = Bitmap.createBitmap(mImageWidth, mImageHeight, Bitmap.Config.ARGB_8888);
            b.setHasAlpha(false);
            byteBuf.put(mRegisterImage);
            int[] intbuffer = new int[mImageWidth * mImageHeight];
            for (int i = 0; i < intbuffer.length; i++) {
                intbuffer[i] = (int) mRegisterImage[i];
            }
            b.setPixels(intbuffer, 0, mImageWidth, 0, 0, mImageWidth, mImageHeight);
//            mImageViewFingerprint.setImageBitmap(this.toGrayScale(b));
            // String printImage = encodeImage(mRegisterImage);
            String printImage = processPicture(this.toGrayScale(b));

            //Get Image Quality
            double iq = 0.0;
            int img_quality[] = new int[1];
            long q_res = sgfpLib.GetImageQuality(mImageWidth, mImageHeight, mRegisterImage, img_quality);
            if (q_res == SGFDxErrorCode.SGFDX_ERROR_NONE) {
                iq = img_quality[0];
                // txtImageQuality.setText("" + iq);
                System.err.println(TAG + "Image Quality: " + iq);
            } else {
                System.err.println(TAG + "Unable To Get Image Quality");
            }

            //Generate Template
        /* dwTimeStart = System.currentTimeMillis();
            result = sgfpLib.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400);
            dwTimeEnd = System.currentTimeMillis();
            dwTimeElapsed = dwTimeEnd - dwTimeStart;
            debugMessage("SetTemplateFormat(SG400) ret: " + result + " [" + dwTimeElapsed + " ms]\n");
            SGFingerInfo fpInfo = new SGFingerInfo();
            for (int i = 0; i < mRegisterTemplate.length; i++) {
                mRegisterTemplate[i] = 0;
            }
            dwTimeStart = System.currentTimeMillis();
            result = sgfpLib.CreateTemplate(fpInfo, mRegisterImage, mRegisterTemplate);


            dwTimeEnd = System.currentTimeMillis();
            dwTimeElapsed = dwTimeEnd - dwTimeStart;

            // debugMessage("Finger Print Template: " + printImage);
            debugMessage("CreateTemplate() ret: " + result + " [" + dwTimeElapsed + " ms]\n");*/

            sgfpLib.CloseDevice();
            res.put("printImage", printImage);
//            res.put("template",android.util.Base64.encodeToString(mRegisterTemplate, android.util.Base64.NO_WRAP));
            res.put("imageQuality", iq);
            res.put("mImageWidth", mImageWidth);
            res.put("mImageHeight", mImageHeight);
            return res;
        } catch (Exception e) {
            debugMessage("Error: " + e.getMessage());
            try {
                res.put("error", e.getMessage());
            } catch (JSONException je) {
                debugMessage(TAG + " " + je.getMessage());
            }

            return res;
        }

    }


    public JSONObject generateTemplate(ImageView mImageViewFingerprint) {
        JSONObject res = new JSONObject();
        try {
            //Init Device
            long openError = sgfpLib.OpenDevice(0);
            debugMessage("Open Device Response: " + openError);

            //Get Device Info
            SGDeviceInfoParam deviceInfo = getDeviceInfo();
            mImageWidth = deviceInfo.imageWidth;
            mImageHeight = deviceInfo.imageHeight;

            //Initialize Bitmap Image
            grayBuffer = new int[JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES * JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES];
            for (int i = 0; i < grayBuffer.length; ++i)
                grayBuffer[i] = android.graphics.Color.GRAY;
            grayBitmap = Bitmap.createBitmap(JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES, JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES, Bitmap.Config.ARGB_8888);
            grayBitmap.setPixels(grayBuffer, 0, JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES, 0, 0, JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES, JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES);
            mImageViewFingerprint.setImageBitmap(grayBitmap);

            if (mRegisterImage != null) {
                mRegisterImage = null;
            }

            //Capture Print
            mMaxTemplateSize = new int[1];
            sgfpLib.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400);
            sgfpLib.GetMaxTemplateSize(mMaxTemplateSize);

            long dwTimeStart = 0, dwTimeEnd = 0, dwTimeElapsed = 0;
            debugMessage("Clicked REGISTER\n");
            mRegisterTemplate = new byte[mMaxTemplateSize[0]];
            mRegisterImage = new byte[mImageWidth * mImageHeight];
            ByteBuffer byteBuf = ByteBuffer.allocate(mImageWidth * mImageHeight);
            dwTimeStart = System.currentTimeMillis();
            long result = sgfpLib.GetImage(mRegisterImage);
            dwTimeEnd = System.currentTimeMillis();
            dwTimeElapsed = dwTimeEnd - dwTimeStart;
            debugMessage("GetImage() ret: " + result + "[" + dwTimeElapsed + " ms]\n");
            Bitmap b = Bitmap.createBitmap(mImageWidth, mImageHeight, Bitmap.Config.ARGB_8888);
            b.setHasAlpha(false);
            byteBuf.put(mRegisterImage);
            int[] intbuffer = new int[mImageWidth * mImageHeight];
            for (int i = 0; i < intbuffer.length; i++) {
                intbuffer[i] = (int) mRegisterImage[i];
            }
            b.setPixels(intbuffer, 0, mImageWidth, 0, 0, mImageWidth, mImageHeight);
            mImageViewFingerprint.setImageBitmap(this.toGrayScale(b));
            // String printImage = encodeImage(mRegisterImage);
//            String printImage = processPicture(this.toGrayScale(b));

            //Get Image Quality
            double iq = 0.0;
            int img_quality[] = new int[1];
            long q_res = sgfpLib.GetImageQuality(mImageWidth, mImageHeight, mRegisterImage, img_quality);
            if (q_res == SGFDxErrorCode.SGFDX_ERROR_NONE) {
                iq = img_quality[0];
                // txtImageQuality.setText("" + iq);
                System.err.println(TAG + "Image Quality: " + iq);
            } else {
                System.err.println(TAG + "Unable To Get Image Quality");
            }

            //Generate Template
            dwTimeStart = System.currentTimeMillis();
            result = sgfpLib.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400);
            dwTimeEnd = System.currentTimeMillis();
            dwTimeElapsed = dwTimeEnd - dwTimeStart;
            debugMessage("SetTemplateFormat(SG400) ret: " + result + " [" + dwTimeElapsed + " ms]\n");
            SGFingerInfo fpInfo = new SGFingerInfo();
            for (int i = 0; i < mRegisterTemplate.length; i++) {
                mRegisterTemplate[i] = 0;
            }
            dwTimeStart = System.currentTimeMillis();
            result = sgfpLib.CreateTemplate(fpInfo, mRegisterImage, mRegisterTemplate);

            dwTimeEnd = System.currentTimeMillis();
            dwTimeElapsed = dwTimeEnd - dwTimeStart;

            // debugMessage("Finger Print Template: " + printImage);
            debugMessage("CreateTemplate() ret: " + result + " [" + dwTimeElapsed + " ms]\n");

            sgfpLib.CloseDevice();

            //res.put("printImage",printImage);
            res.put("template", android.util.Base64.encodeToString(mRegisterTemplate, android.util.Base64.NO_WRAP));
            res.put("imageQuality", iq);
            res.put("mImageWidth", mImageWidth);
            res.put("mImageHeight", mImageHeight);
            return res;
        } catch (Exception e) {
            debugMessage("Error: " + e.getMessage());
            try {
                res.put("error", e.getMessage());
            } catch (JSONException je) {
                debugMessage(TAG + " " + je.getMessage());
            }
            return res;
        }
    }


    public JSONObject verify(ImageView mImageViewFingerprint, String prints) {
        JSONObject res = new JSONObject();
        try {
            //Init Device
            long openError = sgfpLib.OpenDevice(0);
            debugMessage("Open Device Response: " + openError);

            //Get Device Info
            SGDeviceInfoParam deviceInfo = getDeviceInfo();
            mImageWidth = deviceInfo.imageWidth;
            mImageHeight = deviceInfo.imageHeight;

            //Initialize Bitmap Image
            grayBuffer = new int[JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES * JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES];
            for (int i = 0; i < grayBuffer.length; ++i)
                grayBuffer[i] = android.graphics.Color.GRAY;
            grayBitmap = Bitmap.createBitmap(JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES, JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES, Bitmap.Config.ARGB_8888);
            grayBitmap.setPixels(grayBuffer, 0, JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES, 0, 0, JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES, JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES);
            mImageViewFingerprint.setImageBitmap(grayBitmap);

            if (mVerifyImage != null) {
                mVerifyImage = null;
            }

            //Capture Print
            mMaxTemplateSize = new int[1];
            sgfpLib.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400);
            sgfpLib.GetMaxTemplateSize(mMaxTemplateSize);

            long dwTimeStart = 0, dwTimeEnd = 0, dwTimeElapsed = 0;
            debugMessage("Clicked REGISTER\n");
            mVerifyTemplate = new byte[mMaxTemplateSize[0]];
            mVerifyImage = new byte[mImageWidth * mImageHeight];
            ByteBuffer byteBuf = ByteBuffer.allocate(mImageWidth * mImageHeight);
            dwTimeStart = System.currentTimeMillis();
            long result = sgfpLib.GetImage(mVerifyImage);
            dwTimeEnd = System.currentTimeMillis();
            dwTimeElapsed = dwTimeEnd - dwTimeStart;
            debugMessage("GetImage() ret: " + result + "[" + dwTimeElapsed + " ms]\n");
            Bitmap b = Bitmap.createBitmap(mImageWidth, mImageHeight, Bitmap.Config.ARGB_8888);
            b.setHasAlpha(false);
            byteBuf.put(mVerifyImage);
            int[] intbuffer = new int[mImageWidth * mImageHeight];
            for (int i = 0; i < intbuffer.length; i++) {
                intbuffer[i] = (int) mVerifyImage[i];
            }
            b.setPixels(intbuffer, 0, mImageWidth, 0, 0, mImageWidth, mImageHeight);
            mImageViewFingerprint.setImageBitmap(this.toGrayScale(b));
            // String printImage = encodeImage(mRegisterImage);
            String printImage = processPicture(this.toGrayScale(b));

            //Get Image Quality
            double iq = 0.0;
            int img_quality[] = new int[1];
            long q_res = sgfpLib.GetImageQuality(mImageWidth, mImageHeight, mVerifyImage, img_quality);
            if (q_res == SGFDxErrorCode.SGFDX_ERROR_NONE) {
                iq = img_quality[0];
                // txtImageQuality.setText("" + iq);
                System.err.println(TAG + "Image Quality: " + iq);
            } else {
                System.err.println(TAG + "Unable To Get Image Quality");
            }

            //Generate Template
            dwTimeStart = System.currentTimeMillis();
            result = sgfpLib.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400);
            dwTimeEnd = System.currentTimeMillis();
            dwTimeElapsed = dwTimeEnd - dwTimeStart;
            debugMessage("SetTemplateFormat(SG400) ret: " + result + " [" + dwTimeElapsed + " ms]\n");
            SGFingerInfo fpInfo = new SGFingerInfo();
            for (int i = 0; i < mVerifyTemplate.length; i++) {
                mVerifyTemplate[i] = 0;
            }
            dwTimeStart = System.currentTimeMillis();
            result = sgfpLib.CreateTemplate(fpInfo, mVerifyImage, mVerifyTemplate);
            dwTimeEnd = System.currentTimeMillis();
            dwTimeElapsed = dwTimeEnd - dwTimeStart;

            // debugMessage("Finger Print Template: " + printImage);
            debugMessage("CreateTemplate() ret: " + result + " [" + dwTimeElapsed + " ms]\n");

            //Match Template
            for (int i = 0; i < mVerifyTemplate.length; ++i)
                mVerifyTemplate[i] = 0;
            dwTimeStart = System.currentTimeMillis();
            result = sgfpLib.CreateTemplate(fpInfo, mVerifyImage, mVerifyTemplate);
//            DumpFile("verify.min", mVerifyTemplate);
            dwTimeEnd = System.currentTimeMillis();
            dwTimeElapsed = dwTimeEnd - dwTimeStart;
            debugMessage("CreateTemplate() ret:" + result + " [" + dwTimeElapsed + "ms]\n");
            boolean[] matched = new boolean[1];
            dwTimeStart = System.currentTimeMillis();
            JSONArray serverPrints = new JSONArray(prints);
            boolean verified = false;
            for (int i = 0; i < serverPrints.length(); i++) {
                byte[] print = android.util.Base64.decode(serverPrints.getString(i), android.util.Base64.NO_WRAP);
                result = sgfpLib.MatchTemplate(print, mVerifyTemplate, SGFDxSecurityLevel.SL_NORMAL, matched);
                if (matched[0]) {
                    verified = true;
                }
            }
            dwTimeEnd = System.currentTimeMillis();
            dwTimeElapsed = dwTimeEnd - dwTimeStart;
            debugMessage("MatchTemplate() ret:" + result + " [" + dwTimeElapsed + "ms]\n");
            if (verified) {
                Toast.makeText(context, "Authorized", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(context, "Not Authorized", Toast.LENGTH_LONG).show();
            }


            sgfpLib.CloseDevice();
            res.put("response_code","1");
            res.put("printImage",printImage);
//            res.put("template",android.util.Base64.encodeToString(mRegisterTemplate, android.util.Base64.NO_WRAP));
            res.put("imageQuality",iq);
            res.put("mImageWidth",mImageWidth);
            res.put("mImageHeight",mImageHeight);
            return res;
        } catch (Exception e) {
            debugMessage("Error: " + e.getMessage());
            try {
                res.put("error", e.getMessage());
            } catch (JSONException je) {
                debugMessage(TAG + " " + je.getMessage());
            }

            return res;
        }
    }

    public String processPicture(Bitmap bitmap) {
        int mQuality = 80;
        ByteArrayOutputStream jpeg_data = new ByteArrayOutputStream();
        String js_out = "";
        try {
            if (bitmap.compress(CompressFormat.JPEG, mQuality, jpeg_data)) {
                byte[] code = jpeg_data.toByteArray();
                byte[] output = android.util.Base64.encode(code, android.util.Base64.NO_WRAP);
                js_out = new String(output);
                // this.callbackContext.success(js_out);
                //Clean Up
                jpeg_data = null;
                output = null;
                code = null;
            }
            return js_out;
        } catch (Exception e) {
            debugMessage(TAG + " Unable To Process Picture");
            return TAG + " " + e.getMessage();
        } finally {
            js_out = null;
        }

    }


    public void validatePrint(String printImage) {
        try {
            JSONObject requestObj = new JSONObject();
            requestObj.put("base64Imag", printImage);
            requestObj.put("deviceId", "");
            new DoRegisterTemplateTask(requestObj).execute("");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    //Converts ByteArray to Base64 String
    public static String encodeImage(byte[] imageByteArray) {
        StringBuilder sb = new StringBuilder();
        //sb.append("data:image/png;base64,");
        sb.append(StringUtils.newStringUtf8(Base64.encodeBase64(imageByteArray, false)));

        return sb.toString();
        //return Base64.encodeBase64URLSafeString(imageByteArray);
    }


    //Converts image to grayscale (NEW)
    public Bitmap toGrayScale(Bitmap bmpOriginal) {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();
        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                int color = bmpOriginal.getPixel(x, y);
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;
                int gray = (r + g + b) / 3;
                color = Color.rgb(gray, gray, gray);
                //color = Color.rgb(r/3, g/3, b/3);
                bmpGrayscale.setPixel(x, y, color);
            }
        }
        return bmpGrayscale;
    }

    public Bitmap toBinary(Bitmap bmpOriginal) {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();
        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }

    public void testLED() {
        //Init Device
//        long error = sgfpLib.Init(SGFDxDeviceName.SG_DEV_AUTO);
//        debugMessage("Init Response: " + error);
        long openError = sgfpLib.OpenDevice(0);
        debugMessage("Open Device Response: " + openError);

        //Get Device Info
        SGDeviceInfoParam deviceInfo = getDeviceInfo();
        mImageWidth = deviceInfo.imageWidth;
        mImageHeight = deviceInfo.imageHeight;

//        this.mCheckBoxMatched.setChecked(false);
        long dwTimeStart = 0, dwTimeEnd = 0, dwTimeElapsed = 0;
        mLed = !mLed;
        dwTimeStart = System.currentTimeMillis();
        long result = sgfpLib.SetLedOn(mLed);
        dwTimeEnd = System.currentTimeMillis();
        dwTimeElapsed = dwTimeEnd - dwTimeStart;
        debugMessage("setLedOn(" + mLed + ") ret:" + result + " [" + dwTimeElapsed + "ms]\n");
//        mTextViewResult.setText("setLedOn(" + mLed +") ret: " + result + " [" + dwTimeElapsed + "ms]\n");
        sgfpLib.CloseDevice();
    }

    private class DoRegisterTemplateTask extends AsyncTask<String, Void, String> {
        private JSONObject requestObj;

        public DoRegisterTemplateTask(JSONObject requestObj) {
            this.requestObj = requestObj;
        }

        protected void onPreExecute() {
        }

        @Override
        protected String doInBackground(String... urls) {
            try {
                EQBio eb = new EQBio();
                JSONObject response = eb.validate("10.3.26.86", "8181", requestObj);
                return response.toString();
            } catch (Exception e) {
                debugMessage(e.getMessage());
                e.printStackTrace();
                return "{'error':" + e.getMessage() + "}";
            }
        }

        @Override
        protected void onPostExecute(String result) {
            try {
                JSONObject res = new JSONObject(result);
                debugMessage("Response: " + res);
            } catch (JSONException e) {
                debugMessage("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }


}


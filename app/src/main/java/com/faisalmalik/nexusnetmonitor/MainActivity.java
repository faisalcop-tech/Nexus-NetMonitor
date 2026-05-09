package com.faisalmalik.nexusnetmonitor;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "NexusNetMonitor";
    private static final int PERMISSIONS_REQUEST_CODE = 100;
    private static final long CELL_SCAN_INTERVAL = 3000; // 3 seconds

    private WebView webView;
    private TelephonyManager telephonyManager;
    private SubscriptionManager subscriptionManager;
    private LocationManager locationManager;
    private Handler handler;
    private Runnable cellScanRunnable;

    private Location lastLocation;
    private JSONArray cellLog = new JSONArray();
    private int measurementCount = 0;

    private static final String[] REQUIRED_PERMISSIONS = {
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full screen immersive
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Hide system UI
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
        );

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        handler = new Handler(Looper.getMainLooper());

        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        subscriptionManager = SubscriptionManager.from(this);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        setupWebView();
        checkAndRequestPermissions();
        startCellMonitorService();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(false);
        }

        // JavaScript bridge
        webView.addJavascriptInterface(new NexusBridge(), "NexusAndroid");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onConsoleMessage(android.webkit.ConsoleMessage cm) {
                Log.d(TAG, "JS: " + cm.message());
                return;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "Page loaded: " + url);
                startRealTimeUpdates();
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    // ══ JAVASCRIPT BRIDGE ══
    public class NexusBridge {

        @JavascriptInterface
        public String getCellData() {
            return collectAllCellData().toString();
        }

        @JavascriptInterface
        public String getSimInfo() {
            return getSimInformation().toString();
        }

        @JavascriptInterface
        public String getGpsData() {
            return getGPSData().toString();
        }

        @JavascriptInterface
        public String getNetworkType() {
            return getNetworkTypeInfo().toString();
        }

        @JavascriptInterface
        public String getCellLog() {
            return cellLog.toString();
        }

        @JavascriptInterface
        public int getMeasurementCount() {
            return measurementCount;
        }

        @JavascriptInterface
        public String getDeviceInfo() {
            JSONObject info = new JSONObject();
            try {
                info.put("manufacturer", Build.MANUFACTURER);
                info.put("model", Build.MODEL);
                info.put("android", Build.VERSION.RELEASE);
                info.put("sdk", Build.VERSION.SDK_INT);
            } catch (Exception e) { Log.e(TAG, e.getMessage()); }
            return info.toString();
        }

        @JavascriptInterface
        public void showToast(String msg) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public boolean isLocationEnabled() {
            return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                   locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        }

        @JavascriptInterface
        public boolean isInternetAvailable() {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        }
    }

    // ══ COLLECT ALL CELL DATA ══
    @SuppressLint({"MissingPermission"})
    private JSONObject collectAllCellData() {
        JSONObject result = new JSONObject();
        try {
            JSONArray sim1Cells = new JSONArray();
            JSONArray sim2Cells = new JSONArray();

            if (!hasPermission(Manifest.permission.READ_PHONE_STATE) ||
                !hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                result.put("error", "Permissions not granted");
                return result;
            }

            List<SubscriptionInfo> subList = subscriptionManager.getActiveSubscriptionInfoList();
            int simCount = subList != null ? subList.size() : 0;
            result.put("simCount", simCount);

            // Get all cell info
            List<CellInfo> allCells = telephonyManager.getAllCellInfo();
            if (allCells != null) {
                for (CellInfo cell : allCells) {
                    JSONObject cellObj = parseCellInfo(cell);
                    if (cellObj != null) {
                        // Assign to SIM based on registered status
                        if (cell.isRegistered()) {
                            sim1Cells.put(cellObj);
                        } else {
                            sim2Cells.put(cellObj);
                        }
                        measurementCount++;
                        logCell(cellObj);
                    }
                }
            }

            // Multi-SIM: get info for each subscription
            if (subList != null) {
                JSONArray simsArray = new JSONArray();
                for (SubscriptionInfo sub : subList) {
                    JSONObject simInfo = new JSONObject();
                    TelephonyManager tm = telephonyManager.createForSubscriptionId(sub.getSubscriptionId());
                    simInfo.put("simSlot", sub.getSimSlotIndex() + 1);
                    simInfo.put("subId", sub.getSubscriptionId());
                    simInfo.put("displayName", sub.getDisplayName() != null ? sub.getDisplayName().toString() : "");
                    simInfo.put("carrierName", sub.getCarrierName() != null ? sub.getCarrierName().toString() : "");
                    simInfo.put("mcc", sub.getMccString() != null ? sub.getMccString() : "");
                    simInfo.put("mnc", sub.getMncString() != null ? sub.getMncString() : "");
                    simInfo.put("number", sub.getNumber() != null ? sub.getNumber() : "");
                    simInfo.put("dataRoaming", sub.getDataRoaming());
                    simInfo.put("networkType", getNetworkTypeName(tm.getDataNetworkType()));
                    simInfo.put("serviceState", getServiceStateName(tm));

                    // Get cells for this subscription
                    List<CellInfo> subCells = tm.getAllCellInfo();
                    JSONArray subCellsArr = new JSONArray();
                    if (subCells != null) {
                        for (CellInfo c : subCells) {
                            JSONObject co = parseCellInfo(c);
                            if (co != null) subCellsArr.put(co);
                        }
                    }
                    simInfo.put("cells", subCellsArr);
                    simsArray.put(simInfo);
                }
                result.put("sims", simsArray);
            }

            result.put("sim1Cells", sim1Cells);
            result.put("sim2Cells", sim2Cells);
            result.put("timestamp", System.currentTimeMillis());

        } catch (Exception e) {
            Log.e(TAG, "collectAllCellData: " + e.getMessage());
            try { result.put("error", e.getMessage()); } catch (Exception ignored) {}
        }
        return result;
    }

    // ══ PARSE SINGLE CELL INFO ══
    private JSONObject parseCellInfo(CellInfo cell) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("registered", cell.isRegistered());
            obj.put("timestamp", cell.getTimeStamp());

            if (cell instanceof CellInfoLte) {
                CellInfoLte lte = (CellInfoLte) cell;
                CellIdentityLte id = lte.getCellIdentity();
                CellSignalStrengthLte sig = lte.getCellSignalStrength();

                obj.put("type", "LTE");
                obj.put("mcc", id.getMccString() != null ? id.getMccString() : "");
                obj.put("mnc", id.getMncString() != null ? id.getMncString() : "");
                obj.put("tac", id.getTac());
                obj.put("ci", id.getCi());
                obj.put("enb", id.getCi() / 256);
                obj.put("lcid", id.getCi() % 256);
                obj.put("pci", id.getPci());
                obj.put("earfcn", id.getEarfcn());
                obj.put("band", getBandFromEarfcn(id.getEarfcn()));
                obj.put("rsrp", sig.getRsrp());
                obj.put("rsrq", sig.getRsrq());
                obj.put("rssnr", sig.getRssnr());
                obj.put("rssi", sig.getRssi());
                obj.put("cqi", sig.getCqi());
                obj.put("ta", sig.getTimingAdvance());
                obj.put("level", sig.getLevel());
                obj.put("operator", getOperatorName(id.getMccString(), id.getMncString()));

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    obj.put("bandwidth", id.getBandwidth());
                }

            } else if (cell instanceof CellInfoNr) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    CellInfoNr nr = (CellInfoNr) cell;
                    CellIdentityNr id = (CellIdentityNr) nr.getCellIdentity();
                    CellSignalStrengthNr sig = (CellSignalStrengthNr) nr.getCellSignalStrength();

                    obj.put("type", "NR_5G");
                    obj.put("mcc", id.getMccString() != null ? id.getMccString() : "");
                    obj.put("mnc", id.getMncString() != null ? id.getMncString() : "");
                    obj.put("tac", id.getTac());
                    obj.put("nci", id.getNci());
                    obj.put("pci", id.getPci());
                    obj.put("nrarfcn", id.getNrarfcn());
                    obj.put("ssRsrp", sig.getSsRsrp());
                    obj.put("ssRsrq", sig.getSsRsrq());
                    obj.put("ssSinr", sig.getSsSinr());
                    obj.put("level", sig.getLevel());
                    obj.put("operator", getOperatorName(id.getMccString(), id.getMncString()));
                }

            } else if (cell instanceof CellInfoWcdma) {
                CellInfoWcdma wcdma = (CellInfoWcdma) cell;
                CellIdentityWcdma id = wcdma.getCellIdentity();
                CellSignalStrengthWcdma sig = wcdma.getCellSignalStrength();

                obj.put("type", "WCDMA_3G");
                obj.put("mcc", id.getMccString() != null ? id.getMccString() : "");
                obj.put("mnc", id.getMncString() != null ? id.getMncString() : "");
                obj.put("lac", id.getLac());
                obj.put("cid", id.getCid());
                obj.put("psc", id.getPsc());
                obj.put("uarfcn", id.getUarfcn());
                obj.put("rscp", sig.getDbm());
                obj.put("level", sig.getLevel());
                obj.put("operator", getOperatorName(id.getMccString(), id.getMncString()));

            } else if (cell instanceof CellInfoGsm) {
                CellInfoGsm gsm = (CellInfoGsm) cell;
                CellIdentityGsm id = gsm.getCellIdentity();
                CellSignalStrengthGsm sig = gsm.getCellSignalStrength();

                obj.put("type", "GSM_2G");
                obj.put("mcc", id.getMccString() != null ? id.getMccString() : "");
                obj.put("mnc", id.getMncString() != null ? id.getMncString() : "");
                obj.put("lac", id.getLac());
                obj.put("cid", id.getCid());
                obj.put("arfcn", id.getArfcn());
                obj.put("bsic", id.getBsic());
                obj.put("rssi", sig.getDbm());
                obj.put("ber", sig.getBitErrorRate());
                obj.put("level", sig.getLevel());
                obj.put("operator", getOperatorName(id.getMccString(), id.getMncString()));
            }

            return obj;
        } catch (Exception e) {
            Log.e(TAG, "parseCellInfo: " + e.getMessage());
            return null;
        }
    }

    // ══ SIM INFORMATION ══
    @SuppressLint("MissingPermission")
    private JSONObject getSimInformation() {
        JSONObject result = new JSONObject();
        try {
            List<SubscriptionInfo> subs = subscriptionManager.getActiveSubscriptionInfoList();
            if (subs == null) {
                result.put("simCount", 0);
                return result;
            }
            result.put("simCount", subs.size());
            JSONArray simsArr = new JSONArray();
            for (SubscriptionInfo sub : subs) {
                JSONObject s = new JSONObject();
                TelephonyManager tm = telephonyManager.createForSubscriptionId(sub.getSubscriptionId());
                s.put("slot", sub.getSimSlotIndex() + 1);
                s.put("carrier", sub.getCarrierName() != null ? sub.getCarrierName().toString() : "");
                s.put("number", sub.getNumber() != null ? sub.getNumber() : "");
                s.put("mcc", sub.getMccString() != null ? sub.getMccString() : "");
                s.put("mnc", sub.getMncString() != null ? sub.getMncString() : "");
                s.put("country", sub.getCountryIso() != null ? sub.getCountryIso().toUpperCase() : "");
                s.put("roaming", sub.getDataRoaming() == 1);
                s.put("networkType", getNetworkTypeName(tm.getDataNetworkType()));
                s.put("networkTypeVoice", getNetworkTypeName(tm.getVoiceNetworkType()));
                s.put("operatorName", tm.getNetworkOperatorName());
                s.put("serviceState", getServiceStateName(tm));
                s.put("volteEnabled", tm.isVolteAvailable() || tm.isImsRegistered());
                simsArr.put(s);
            }
            result.put("sims", simsArr);
        } catch (Exception e) {
            try { result.put("error", e.getMessage()); } catch (Exception ignored) {}
        }
        return result;
    }

    // ══ GPS DATA ══
    private JSONObject getGPSData() {
        JSONObject gps = new JSONObject();
        try {
            if (lastLocation != null) {
                gps.put("lat", lastLocation.getLatitude());
                gps.put("lng", lastLocation.getLongitude());
                gps.put("accuracy", lastLocation.getAccuracy());
                gps.put("altitude", lastLocation.getAltitude());
                gps.put("bearing", lastLocation.getBearing());
                gps.put("speed", lastLocation.getSpeed() * 2.237); // m/s to mph
                gps.put("provider", lastLocation.getProvider());
                gps.put("time", lastLocation.getTime());
                gps.put("hasGps", true);
            } else {
                gps.put("hasGps", false);
                gps.put("lat", 33.5540);
                gps.put("lng", 73.0531);
            }
        } catch (Exception e) {
            try { gps.put("error", e.getMessage()); } catch (Exception ignored) {}
        }
        return gps;
    }

    // ══ NETWORK TYPE INFO ══
    @SuppressLint("MissingPermission")
    private JSONObject getNetworkTypeInfo() {
        JSONObject info = new JSONObject();
        try {
            info.put("dataType", getNetworkTypeName(telephonyManager.getDataNetworkType()));
            info.put("voiceType", getNetworkTypeName(telephonyManager.getVoiceNetworkType()));
            info.put("operator", telephonyManager.getNetworkOperatorName());
            info.put("isRoaming", telephonyManager.isNetworkRoaming());
            info.put("phoneType", telephonyManager.getPhoneType());

            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm != null ? cm.getActiveNetworkInfo() : null;
            info.put("connected", ni != null && ni.isConnected());
            info.put("connectionType", ni != null ? ni.getTypeName() : "NONE");
        } catch (Exception e) {
            try { info.put("error", e.getMessage()); } catch (Exception ignored) {}
        }
        return info;
    }

    // ══ START REAL-TIME UPDATES ══
    private void startRealTimeUpdates() {
        if (cellScanRunnable != null) {
            handler.removeCallbacks(cellScanRunnable);
        }

        cellScanRunnable = new Runnable() {
            @Override
            public void run() {
                if (hasPermission(Manifest.permission.READ_PHONE_STATE) &&
                    hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    sendDataToWebView();
                }
                handler.postDelayed(this, CELL_SCAN_INTERVAL);
            }
        };

        handler.post(cellScanRunnable);
        startLocationUpdates();
    }

    private void sendDataToWebView() {
        try {
            JSONObject cellData = collectAllCellData();
            JSONObject simInfo = getSimInformation();
            JSONObject gpsData = getGPSData();
            JSONObject netInfo = getNetworkTypeInfo();

            String cellJson = escapeForJs(cellData.toString());
            String simJson = escapeForJs(simInfo.toString());
            String gpsJson = escapeForJs(gpsData.toString());
            String netJson = escapeForJs(netInfo.toString());

            String js = String.format(
                "if(typeof window.onNexusData==='function'){" +
                "window.onNexusData(%s,%s,%s,%s);" +
                "}",
                cellData.toString(), simInfo.toString(),
                gpsData.toString(), netInfo.toString()
            );

            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    webView.evaluateJavascript(js, null);
                } else {
                    webView.loadUrl("javascript:" + js);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "sendDataToWebView: " + e.getMessage());
        }
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return;

        LocationListener listener = location -> {
            lastLocation = location;
        };

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 2000, 5, listener
                );
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 2000, 5, listener
                );
            }

            // Get last known
            Location gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (gpsLoc != null) lastLocation = gpsLoc;
            else if (netLoc != null) lastLocation = netLoc;

        } catch (Exception e) {
            Log.e(TAG, "Location updates: " + e.getMessage());
        }
    }

    // ══ LOG CELL ══
    private void logCell(JSONObject cell) {
        try {
            JSONObject entry = new JSONObject();
            entry.put("time", new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
            entry.put("cell", cell);
            if (cellLog.length() < 500) {
                cellLog.put(entry);
            }
        } catch (Exception e) {
            Log.e(TAG, "logCell: " + e.getMessage());
        }
    }

    // ══ HELPER METHODS ══
    private String getOperatorName(String mcc, String mnc) {
        if (mcc == null || mnc == null) return "Unknown";
        String code = mcc + mnc;
        switch (code) {
            case "41001": return "Jazz";
            case "41007": return "Warid";
            case "41003": return "Ufone";
            case "41008": return "Zong";
            case "41006": return "Telenor";
            default: return "Unknown (" + code + ")";
        }
    }

    private String getNetworkTypeName(int type) {
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_IDEN:  return "2G";
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_HSPAP: return "3G";
            case TelephonyManager.NETWORK_TYPE_LTE:   return "4G LTE";
            case TelephonyManager.NETWORK_TYPE_NR:    return "5G NR";
            default:                                   return "Unknown";
        }
    }

    private String getServiceStateName(TelephonyManager tm) {
        try {
            ServiceState ss = tm.getServiceState();
            if (ss == null) return "Unknown";
            switch (ss.getState()) {
                case ServiceState.STATE_IN_SERVICE:     return "In Service";
                case ServiceState.STATE_OUT_OF_SERVICE: return "No Service";
                case ServiceState.STATE_EMERGENCY_ONLY: return "Emergency Only";
                case ServiceState.STATE_POWER_OFF:      return "Power Off";
                default:                                return "Unknown";
            }
        } catch (Exception e) { return "Unknown"; }
    }

    private String getBandFromEarfcn(int earfcn) {
        if (earfcn >= 0 && earfcn <= 599)       return "B1 (2100 MHz)";
        if (earfcn >= 600 && earfcn <= 1199)     return "B2 (1900 MHz)";
        if (earfcn >= 1200 && earfcn <= 1949)    return "B3 (1800 MHz)";
        if (earfcn >= 1950 && earfcn <= 2399)    return "B4 (1700 MHz)";
        if (earfcn >= 2400 && earfcn <= 2649)    return "B5 (850 MHz)";
        if (earfcn >= 2750 && earfcn <= 3449)    return "B7 (2600 MHz)";
        if (earfcn >= 3450 && earfcn <= 3799)    return "B8 (900 MHz)";
        if (earfcn >= 6150 && earfcn <= 6449)    return "B20 (800 MHz)";
        if (earfcn >= 9870 && earfcn <= 9919)    return "B28 (700 MHz)";
        if (earfcn >= 36000 && earfcn <= 36199)  return "B33 (TDD)";
        if (earfcn >= 37750 && earfcn <= 38249)  return "B38 (2600 TDD)";
        if (earfcn >= 38250 && earfcn <= 38649)  return "B39 (1900 TDD)";
        if (earfcn >= 38650 && earfcn <= 39649)  return "B40 (2300 TDD)";
        if (earfcn >= 41590 && earfcn <= 43589)  return "B41 (2500 TDD)";
        return "Band " + earfcn;
    }

    private String escapeForJs(String json) {
        return json.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    // ══ PERMISSIONS ══
    private void checkAndRequestPermissions() {
        List<String> missing = new ArrayList<>();
        for (String p : REQUIRED_PERMISSIONS) {
            if (!hasPermission(p)) missing.add(p);
        }
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missing.toArray(new String[0]),
                PERMISSIONS_REQUEST_CODE
            );
        } else {
            startRealTimeUpdates();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            startRealTimeUpdates();
        }
    }

    // ══ SERVICE ══
    private void startCellMonitorService() {
        Intent serviceIntent = new Intent(this, CellMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && cellScanRunnable != null) {
            handler.removeCallbacks(cellScanRunnable);
        }
        if (webView != null) {
            webView.destroy();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}

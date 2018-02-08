package com.okamipride.scanlanip;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.DialogInterface.OnKeyListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.okamipride.scanlanip.scan.IpUtil;
import com.okamipride.scanlanip.scan.NetUtil;
import com.okamipride.scanlanip.scan.Network;
import com.okamipride.scanlanip.scan.ScanIp;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ScanMainActivity extends Activity implements OnClickListener{

    private static final String TAG = "ScanMainActivity";

	private List<Device> scanResult = null;

    private TextView tvMyselfIp;
    private TextView tvMyselfMac;
    private String myselfIP;

    private TextView tvTotalResult;

    private ListIpAdapter ipAdpater = null;
    private ListView lstview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_main);

        scanResult = new ArrayList<Device>();
        ipAdpater = new ListIpAdapter(scanResult);

        View layoutMyself = findViewById(R.id.include_myself);
        tvMyselfIp = layoutMyself.findViewById(R.id.tv_ip);
        tvMyselfMac = layoutMyself.findViewById(R.id.tv_mac);
        initMysef();

        tvTotalResult = (TextView) findViewById(R.id.tv_total_result);
        updateTotalCount(0);

        lstview = (ListView) findViewById(R.id.lsv_ips);
        lstview.setAdapter(ipAdpater);

        Button discover = (Button) findViewById(R.id.btn_search);
        discover.setOnClickListener(this);
    }

    private void initMysef() {
        Map<String, String> netInfo = NetUtil.getNetworkInfo(this);
        myselfIP = netInfo.get("ip");
        String mac = netInfo.get("mac");
        tvMyselfIp.setText(myselfIP);
        tvMyselfMac.setText(mac);
    }

    @Override
	public void onClick(View v) {
		new ScanIpAsyncTask().execute(this);
	}

    private void updateTotalCount(int count) {
        String format = getString(R.string.total_result);
        String totalResult = String.format(format, count);
        tvTotalResult.setText(totalResult);
    }
    
    class ScanIpAsyncTask extends AsyncTask <Context, String, Boolean> {
		private ProgressDialog syncProgress = null;
		private List<InetAddress> lanIpList;
		private List<Device> deviceList;
		private long taketime = 0;
		private ScanIp scan;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			syncProgress = new ProgressDialog(ScanMainActivity.this);
			syncProgress.setTitle(R.string.sync_prepare_title);
			syncProgress.setCancelable(true);
			syncProgress.setOnKeyListener(new OnKeyListener() {
				@Override
				public boolean onKey(DialogInterface dialog, int keyCode,
						KeyEvent event) {
					Log.e(TAG, "onKey KeyCode: " + String.valueOf(keyCode));
					dialog.cancel();
					if (null != scan)
						scan.onCancell();
					cancel(true);
					return true;
				}
			});
			
			syncProgress.setOnDismissListener(new OnDismissListener() {
				@Override
				public void onDismiss(DialogInterface dialog) {
					Log.e(TAG, "onDismiss()");
			
					if (null != scan)
						scan.onCancell();
					cancel(true);
					Thread.interrupted();
				}
			});
			// dialog show 
			syncProgress.show();
		}
		
		@Override
		protected void onCancelled() {
			super.onCancelled();
			Log.d(TAG, "ScanIpAsyncTask Cancel");
			if (syncProgress != null && syncProgress.isShowing()) {
				syncProgress.dismiss();
				syncProgress = null;
			}
			cancel(true);
		}

		@Override
		protected Boolean doInBackground(Context... arg0) {
			long scanStart = 0; 
			long scanEnd = 0;
			boolean dataSend = false;
			scanStart = System.currentTimeMillis();
			scan = new ScanIp();
			Log.d(TAG, "startScan");
			
			if (!isCancelled()) {
			   	publishProgress (getString(R.string.sync_start_status_discover));
				lanIpList =  scan.startScan(arg0[0]);
				if (lanIpList != null) {
                    deviceList = new ArrayList<Device>();
                    Map<String, String> ipMacs = NetUtil.readArp();
                    for (InetAddress addr : lanIpList) {
                        String ip = addr.getHostAddress();
                        String mac = null;
                        if (ipMacs != null) {
                            mac = ipMacs.get(ip);
                        }
                        deviceList.add(new Device(ip, mac));
                    }
                    dataSend = true;
				}
			}else {
				lanIpList = null;
                deviceList = null;
			}
			
			scanEnd = System.currentTimeMillis();
			taketime = scanEnd - scanStart;
			Log.d(TAG, "endScan taketime = " + Long.toString(taketime));
			
			return dataSend;			
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if (syncProgress != null && syncProgress.isShowing()) {
				syncProgress.dismiss();
				syncProgress = null;
			}

			if (result) {
				scanResult.clear();
				scanResult.addAll(deviceList);
				Log.e(TAG,"scanResult size =" +Integer.toString(scanResult.size()));//finish();
			} else {
				scanResult.clear();
				Log.e(TAG,"false scanResult size =" +Integer.toString(scanResult.size()));//finish();
				//finish();
			}

            int count = (deviceList == null) ? 0 : deviceList.size();
            updateTotalCount(count);
            ipAdpater.notifyDataSetChanged();
        }

        @Override
		protected void onProgressUpdate(String... values) {
			syncProgress.setMessage(values[0]);
		}	
	}
}

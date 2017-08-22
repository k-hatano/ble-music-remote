package jp.nita.blemusicremote;

import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

public class ControllerActivity extends Activity {

	BluetoothAdapter mBluetoothAdapter;
	BluetoothLeScanner mBluetoothLeScanner;
	private BluetoothGatt mBleGatt;
	private BluetoothGattCharacteristic mBleCharacteristic;

	private HashMap<String, BluetoothDevice> foundDevices = new HashMap<String, BluetoothDevice>();

	Handler guiThreadHandler = new Handler();
	final ControllerActivity finalActivity = this;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_controller);

		BluetoothManager bluetoothManager = (BluetoothManager) (this.getSystemService(Context.BLUETOOTH_SERVICE));
		mBluetoothAdapter = bluetoothManager.getAdapter();
		if ((mBluetoothAdapter == null) || (!mBluetoothAdapter.isEnabled())) {
			this.setResult(MainActivity.RESULT_ERROR_FAILED);
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivity(enableBtIntent);
			finish();
			return;
		}

		String macAddress = android.provider.Settings.Secure.getString(finalActivity.getContentResolver(),
				"bluetooth_address");
		TextView controllerTextView = (TextView) findViewById(R.id.textview_controller);
		controllerTextView.setText(macAddress);

		foundDevices = new HashMap<String, BluetoothDevice>();

		mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
		
		setListeners();
		startScanning();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.controller, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	public void startScanning() {
		final ProgressDialog progressDialog = Statics.getProgressDialog(this, getString(R.string.controller_mode), getString(R.string.scanning));
		
		scanPairedDevices();
		scanNewDevice();
		guiThreadHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				progressDialog.cancel();
			}
		}, 10000);

		progressDialog.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface arg0) {
				mBluetoothLeScanner.stopScan(scanCallback);
				if (foundDevices.size() <= 0) {
					OnClickListener listener = new OnClickListener(){
						@Override
						public void onClick(DialogInterface arg0, int arg1) {
							finish();
						}
					};
					
					AlertDialog alertDialog = Statics.getAlertDialog(finalActivity, getString(R.string.player_mode),
							getString(R.string.no_devices_found), listener);
					alertDialog.show();
				} else {
					final String list[] = new String[foundDevices.size()];
					int i = 0;
					for (String address : foundDevices.keySet()) {
						list[i] = address;
						i++;
					}
					new AlertDialog.Builder(finalActivity).setTitle("Select device")
							.setItems(list, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface arg0, int arg1) {
									foundDevices.get(list[arg1]).connectGatt(getApplicationContext(), true,
											mGattCallback);
								}
							}).show();
				}
			}
		});
		
		progressDialog.show();
	}
	
	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				if (gatt.getServices().size() == 0) {
					gatt.discoverServices();
				}
			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				if (mBleGatt.getDevice().getAddress().equals(gatt.getDevice().getAddress())) {
					mBleGatt = null;
				}
				gatt.close();
				TextView playerTextView = (TextView) findViewById(R.id.textview_player);
				playerTextView.setText("");
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				BluetoothGattService service = gatt.getService(UUID.fromString(MainActivity.SERVICE_UUID));
				if (service != null) {
					mBleCharacteristic = service.getCharacteristic(UUID.fromString(MainActivity.CHAR_UUID));

					if (mBleCharacteristic != null) {
						mBleGatt = gatt;
						// TODO: スキャンしているだけの時はいきなりgattに登録しない

						BluetoothGattDescriptor descriptor = mBleCharacteristic
								.getDescriptor(UUID.fromString(MainActivity.CHAR_CONFIG_UUID));

						descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
						mBleGatt.writeDescriptor(descriptor);

						if (!finalActivity.foundDevices.containsKey(mBleGatt.getDevice().getAddress())) {
							finalActivity.foundDevices.put(mBleGatt.getDevice().getAddress(), mBleGatt.getDevice());
						}

						TextView playerTextView = (TextView)findViewById(R.id.textview_player);
						playerTextView.setText(mBleGatt.getDevice().getAddress());
					}
				}
			}
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

		}
	};

	private void scanPairedDevices() {
		BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
		Set<BluetoothDevice> btDevices = btAdapter.getBondedDevices();
		for (BluetoothDevice device : btDevices) {
			int type = device.getType();
			if (type == BluetoothDevice.DEVICE_TYPE_LE || type == BluetoothDevice.DEVICE_TYPE_DUAL) {
				device.connectGatt(getApplicationContext(), false, mGattCallback);
			}
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	final ScanCallback scanCallback = new ScanCallback() {
		@Override
		public void onScanResult(int callbackType, ScanResult result) {
			super.onScanResult(callbackType, result);
			result.getDevice().connectGatt(getApplicationContext(), true, mGattCallback);
		}

		@Override
		public void onScanFailed(int intErrorCode) {
			super.onScanFailed(intErrorCode);
		}
	};

	private void scanNewDevice() {
		mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
		mBluetoothLeScanner.startScan(scanCallback);
	}

	private final LeScanCallback mScanCallback = new BluetoothAdapter.LeScanCallback() {
		@Override
		public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					BluetoothGatt gatt = device.connectGatt(getApplicationContext(), false, mGattCallback);
				}
			});
		}
	};

	@Override
	protected void onDestroy() {
		TextView controllerTextView = (TextView) findViewById(R.id.textview_controller);
		controllerTextView.setText("");
		TextView playerTextView = (TextView)findViewById(R.id.textview_player);
		playerTextView.setText("");
		
		if (mBleGatt != null) {
			mBleGatt.close();
			mBleGatt = null;
		}
		super.onDestroy();
	}
	
	public void setListeners(){
		findViewById(R.id.button_play_plause).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mBleGatt == null) {
					return;
				}

				byte[] bytes = { 00 };
				mBleCharacteristic.setValue(bytes);
				mBleGatt.writeCharacteristic(mBleCharacteristic);
			}
		});
		
		findViewById(R.id.button_rewind).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mBleGatt == null) {
					return;
				}

				byte[] bytes = { 01 };
				mBleCharacteristic.setValue(bytes);
				mBleGatt.writeCharacteristic(mBleCharacteristic);
			}
		});
		
		findViewById(R.id.button_forward).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mBleGatt == null) {
					return;
				}

				byte[] bytes = { 02 };
				mBleCharacteristic.setValue(bytes);
				mBleGatt.writeCharacteristic(mBleCharacteristic);
			}
		});
		
		findViewById(R.id.button_plus).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mBleGatt == null) {
					return;
				}

				byte[] bytes = { 03 };
				mBleCharacteristic.setValue(bytes);
				mBleGatt.writeCharacteristic(mBleCharacteristic);
			}
		});
		
		findViewById(R.id.button_minus).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mBleGatt == null) {
					return;
				}

				byte[] bytes = { 04 };
				mBleCharacteristic.setValue(bytes);
				mBleGatt.writeCharacteristic(mBleCharacteristic);
			}
		});
	}
}

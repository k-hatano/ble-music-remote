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

	BluetoothManager mBluetoothManager;
	BluetoothAdapter mBluetoothAdapter;
	BluetoothLeScanner mBluetoothLeScanner;
	private BluetoothGatt mBleGatt;
	private BluetoothGattCharacteristic mBleCharacteristic;

	ProgressDialog mProgressDialog = null;

	private HashMap<String, BluetoothDevice> foundDevices = new HashMap<String, BluetoothDevice>();
	HashMap<String, BluetoothGatt> scanningDevices = new HashMap<String, BluetoothGatt>();

	Handler guiThreadHandler = new Handler();
	final ControllerActivity finalActivity = this;

	final int STATE_NONE = 0;
	final int STATE_SCANNING = 1;
	final int STATE_PAIRED = 2;
	int state = STATE_NONE;

	static Object bleProcess = new Object();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_controller);

		state = STATE_NONE;

		mBluetoothManager = (BluetoothManager) (this.getSystemService(Context.BLUETOOTH_SERVICE));
		mBluetoothAdapter = mBluetoothManager.getAdapter();
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

		scanningDevices = new HashMap<String, BluetoothGatt>();
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
		String message = getString(R.string.scanning) + "\n" + "0 " + getString(R.string.devices_found);

		mProgressDialog = Statics.getProgressDialog(this, getString(R.string.controller_mode), message);

		state = STATE_SCANNING;
		scanPairedDevices();
		scanNewDevice();
		
		mProgressDialog.setButton(getString(R.string.done), new OnClickListener(){
			@Override
			public void onClick(DialogInterface arg0, int arg1) {
				mProgressDialog.cancel();
			}
		});

		mProgressDialog.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface arg0) {
				state = STATE_NONE;
				mBluetoothLeScanner.stopScan(scanCallback);
				if (foundDevices.size() <= 0) {
					OnClickListener listener = new OnClickListener() {
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
									synchronized (bleProcess) {
										state = STATE_PAIRED;
										foundDevices.get(list[arg1]).connectGatt(getApplicationContext(), false,
												mGattCallback);
									}
								}
							})
							.setOnCancelListener(new OnCancelListener(){
								@Override
								public void onCancel(DialogInterface arg0) {
									ControllerActivity.this.finish();
								}
							})
							.show();
				}
				mProgressDialog = null;
			}
		});

		mProgressDialog.show();
	}

	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			synchronized (bleProcess) {
				if (newState == BluetoothProfile.STATE_CONNECTED) {
					if (gatt.getServices().size() == 0) {
						gatt.discoverServices();
					} else {
						handleServices(gatt);
					}
				} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
					if (scanningDevices.containsKey(gatt.getDevice().getAddress())) {
						scanningDevices.remove(gatt.getDevice().getAddress());
					}
					if (mBleGatt != null && mBleGatt.getDevice().getAddress().equals(gatt.getDevice().getAddress())) {
						OnClickListener listener = new OnClickListener() {
							@Override
							public void onClick(DialogInterface arg0, int arg1) {
								finish();
							}
						};

						AlertDialog alertDialog = Statics.getAlertDialog(finalActivity, getString(R.string.player_mode),
								getString(R.string.device_disconnected), listener);
						alertDialog.show();
					}
					gatt.close();
				}
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				handleServices(gatt);
			}
		}

		public void handleServices(BluetoothGatt gatt) {
			BluetoothGattService service = gatt.getService(UUID.fromString(MainActivity.SERVICE_UUID));
			if (service != null) {
				mBleCharacteristic = service.getCharacteristic(UUID.fromString(MainActivity.CHAR_UUID));

				if (mBleCharacteristic != null) {
					if (state == STATE_SCANNING) {
						if (!finalActivity.foundDevices.containsKey(gatt.getDevice().getAddress())) {
							finalActivity.foundDevices.put(gatt.getDevice().getAddress(), gatt.getDevice());
						}

						if (mProgressDialog != null) {
							String message = getString(R.string.scanning) + "\n" + finalActivity.foundDevices.size()
									+ " " + getString(R.string.devices_found);
							mProgressDialog.setMessage(message);
						}
					} else if (state == STATE_PAIRED) {
						synchronized (bleProcess) {
							mBleGatt = gatt;

							boolean registered = mBleGatt.setCharacteristicNotification(mBleCharacteristic, true);

							BluetoothGattDescriptor descriptor = mBleCharacteristic
									.getDescriptor(UUID.fromString(MainActivity.CHAR_CONFIG_UUID));

							descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
							mBleGatt.writeDescriptor(descriptor);
							mBleGatt.getDevice().createBond();
							
							state = STATE_NONE;

							guiThreadHandler.post(new Runnable() {
								@Override
								public void run() {
									TextView playerTextView = (TextView) findViewById(R.id.textview_player);
									playerTextView.setText(mBleGatt.getDevice().getAddress() + " / " + mBleGatt.getDevice().getName());
								}
							});
						}
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
			synchronized (bleProcess) {
				int type = device.getType();
				if ((type == BluetoothDevice.DEVICE_TYPE_LE || type == BluetoothDevice.DEVICE_TYPE_DUAL)
						&& mBluetoothManager.getConnectionState(device,
								BluetoothProfile.GATT) != BluetoothProfile.STATE_CONNECTING) {
					BluetoothGatt resultGatt = device.connectGatt(getApplicationContext(), false, mGattCallback);
					if (resultGatt != null) {
						scanningDevices.put(resultGatt.getDevice().getAddress(), resultGatt);
					}
				}
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	final ScanCallback scanCallback = new ScanCallback() {
		@Override
		public void onScanResult(int callbackType, ScanResult result) {
			super.onScanResult(callbackType, result);
			synchronized (bleProcess) {
				if (result.getDevice() == null) {
					return;
				}
				int type = result.getDevice().getType();
				if (scanningDevices.size() >= 3 || scanningDevices.containsKey(result.getDevice().getAddress())) {
					return;
				}

				if ((type == BluetoothDevice.DEVICE_TYPE_LE || type == BluetoothDevice.DEVICE_TYPE_DUAL)
						&& mBluetoothManager.getConnectionState(result.getDevice(),
								BluetoothProfile.GATT) != BluetoothProfile.STATE_CONNECTING) {
					BluetoothGatt resultGatt = result.getDevice().connectGatt(getApplicationContext(), false,
							mGattCallback);
					scanningDevices.put(result.getDevice().getAddress(), resultGatt);

					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}

		@Override
		public void onScanFailed(int intErrorCode) {
			super.onScanFailed(intErrorCode);
			
			ControllerActivity.this.setResult(MainActivity.RESULT_ERROR_FAILED);
		}
	};

	private void scanNewDevice() {
		synchronized (bleProcess) {
			mBluetoothLeScanner.startScan(scanCallback);
		}
	}

	@Override
	protected void onDestroy() {
		TextView controllerTextView = (TextView) findViewById(R.id.textview_controller);
		controllerTextView.setText("");
		TextView playerTextView = (TextView) findViewById(R.id.textview_player);
		playerTextView.setText("");

		if (mBleGatt != null) {
			mBleGatt.close();
			mBleGatt = null;
		}
		super.onDestroy();
	}

	public void setListeners() {
		findViewById(R.id.button_play_plause).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				synchronized (bleProcess) {
					if (mBleGatt == null) {
						return;
					}

					byte[] bytes = { 00 };
					mBleCharacteristic.setValue(bytes);
					mBleGatt.writeCharacteristic(mBleCharacteristic);
				}
			}
		});

		findViewById(R.id.button_rewind).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				synchronized (bleProcess) {
					if (mBleGatt == null) {
						return;
					}

					byte[] bytes = { 01 };
					mBleCharacteristic.setValue(bytes);
					mBleGatt.writeCharacteristic(mBleCharacteristic);
				}
			}
		});

		findViewById(R.id.button_forward).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				synchronized (bleProcess) {
					if (mBleGatt == null) {
						return;
					}

					byte[] bytes = { 02 };
					mBleCharacteristic.setValue(bytes);
					mBleGatt.writeCharacteristic(mBleCharacteristic);
				}
			}
		});

		findViewById(R.id.button_plus).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				synchronized (bleProcess) {
					if (mBleGatt == null) {
						return;
					}

					byte[] bytes = { 03 };
					mBleCharacteristic.setValue(bytes);
					mBleGatt.writeCharacteristic(mBleCharacteristic);
				}
			}
		});

		findViewById(R.id.button_minus).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				synchronized (bleProcess) {
					if (mBleGatt == null) {
						return;
					}

					byte[] bytes = { 04 };
					mBleCharacteristic.setValue(bytes);
					mBleGatt.writeCharacteristic(mBleCharacteristic);
				}
			}
		});
		
		findViewById(R.id.button_eject).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				synchronized (bleProcess) {
					if (mBleGatt == null) {
						return;
					}

					mBleGatt.disconnect();
				}
			}
		});
	}
}

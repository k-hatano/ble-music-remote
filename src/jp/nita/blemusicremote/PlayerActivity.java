package jp.nita.blemusicremote;

import java.util.UUID;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Instrumentation;

public class PlayerActivity extends Activity {

	BluetoothGattServer mGattServer;
	BluetoothLeAdvertiser mAdvertiser;
	BluetoothGattCharacteristic mCharacteristic;
	BluetoothDevice mDevice = null;
	AdvertiseCallback mAdvertiseCallback;

	ProgressDialog mProgressDialog = null;
	Handler guiThreadHandler = new Handler();

	final PlayerActivity finalActivity = this;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_player);

		BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		BluetoothAdapter mBluetoothAdapter = bluetoothManager.getAdapter();

		if ((mBluetoothAdapter == null) || (!mBluetoothAdapter.isEnabled())) {
			this.setResult(MainActivity.RESULT_ERROR_FAILED);
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivity(enableBtIntent);
			finish();
			return;
		}

		String macAddress = android.provider.Settings.Secure.getString(finalActivity.getContentResolver(),
				"bluetooth_address");
		TextView controllerTextView = (TextView) findViewById(R.id.textview_player);
		controllerTextView.setText(macAddress);

		mAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
		if (mAdvertiser == null) {
			this.setResult(MainActivity.RESULT_ERROR_FAILED);
			finish();
			return;
		}

		AdvertiseSettings.Builder settingBuilder = new AdvertiseSettings.Builder();
		settingBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
		settingBuilder.setConnectable(true);
		settingBuilder.setTimeout(100000);
		settingBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
		AdvertiseSettings settings = settingBuilder.build();

		AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
		dataBuilder.addServiceUuid(new ParcelUuid(UUID.fromString(MainActivity.SERVICE_UUID)));
		dataBuilder.setIncludeDeviceName(false);
		AdvertiseData advertiseData = dataBuilder.build();

		mGattServer = setGattServer();

		mAdvertiseCallback = new AdvertiseCallback() {
			@Override
			public void onStartSuccess(AdvertiseSettings settingsInEffect) {
				super.onStartSuccess(settingsInEffect);
			}

			@Override
			public void onStartFailure(int errorCode) {
				super.onStartFailure(errorCode);

				PlayerActivity.this.setResult(MainActivity.RESULT_ERROR_FAILED);
				finish();
				return;
			};
		};

		mAdvertiser.startAdvertising(settings, advertiseData, mAdvertiseCallback);
		startAdvertising();
		setListeners();
	}

	@Override
	protected void onDestroy() {
		if (mGattServer != null) {
			mGattServer.clearServices();
			mGattServer.close();
			mGattServer = null;
		}

		if (mAdvertiser != null) {
			mAdvertiser.stopAdvertising(mAdvertiseCallback);
			mAdvertiser = null;
		}

		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.player, menu);
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

	public void startAdvertising() {
		mProgressDialog = Statics.getProgressDialog(this, getString(R.string.player_mode),
				getString(R.string.advertising));

		mProgressDialog.setButton(getString(R.string.done), new OnClickListener() {
			@Override
			public void onClick(DialogInterface arg0, int arg1) {
				mProgressDialog.cancel();
			}
		});

		mProgressDialog.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface arg0) {
				if (mDevice == null) {
					finish();
				}
				mProgressDialog = null;
			}
		});

		mProgressDialog.show();
	}

	public BluetoothGattServer setGattServer() {

		BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

		BluetoothGattServer gatt = manager.openGattServer(getApplicationContext(), new BluetoothGattServerCallback() {
			@Override
			public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
					BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded,
					int offset, byte[] value) {
				super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded,
						offset, value);
				if (value != null) {
					KeyEventSender sender = new KeyEventSender();
					for (byte aByte : value) {
						switch (aByte) {
						case 0:
							sender.execute(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
							break;
						case 1:
							sender.execute(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
							break;
						case 2:
							sender.execute(KeyEvent.KEYCODE_MEDIA_NEXT);
							break;
						case 3:
							sender.execute(KeyEvent.KEYCODE_VOLUME_UP);
							break;
						case 4:
							sender.execute(KeyEvent.KEYCODE_VOLUME_DOWN);
						default:
							break;
						}
					}
				}
				mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
			}

			@Override
			public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
					BluetoothGattCharacteristic characteristic) {
				super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
				mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, "ABC".getBytes());
			}

			@Override
			public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
				super.onConnectionStateChange(device, status, newState);
				if (newState == BluetoothProfile.STATE_CONNECTED) {
					mDevice = device;

					guiThreadHandler.post(new Runnable() {
						@Override
						public void run() {
							TextView controllerTextView = (TextView) findViewById(R.id.textview_controller);
							controllerTextView.setText(mDevice.getAddress() + " / " + mDevice.getName());

							mProgressDialog.cancel();
							Toast.makeText(PlayerActivity.this,
									"Device connected : " + mDevice.getAddress() + " / " + mDevice.getName(),
									Toast.LENGTH_SHORT).show();
						}
					});
				} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
					PlayerActivity.this.setResult(MainActivity.RESULT_ERROR_DISCONNECTED);
					finish();
					mDevice = null;
				}
			}
		});

		if (gatt == null) {
			return null;
		}

		BluetoothGattService service = new BluetoothGattService(UUID.fromString(MainActivity.SERVICE_UUID),
				BluetoothGattService.SERVICE_TYPE_PRIMARY);
		mCharacteristic = new BluetoothGattCharacteristic(UUID.fromString(MainActivity.CHAR_UUID),
				BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_READ
						| BluetoothGattCharacteristic.PROPERTY_WRITE,
				BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
		service.addCharacteristic(mCharacteristic);
		gatt.addService(service);

		return gatt;
	}

	private class KeyEventSender extends AsyncTask<Integer, Object, Object> {
		@Override
		protected Object doInBackground(Integer... params) {
			int keycode = (Integer) (params[0]);
			Instrumentation ist = new Instrumentation();
			ist.sendKeyDownUpSync(keycode);
			return null;
		}
	}

	public void setListeners() {
		findViewById(R.id.button_play_plause).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				KeyEventSender sender = new KeyEventSender();
				sender.execute(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
			}
		});

		findViewById(R.id.button_rewind).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				KeyEventSender sender = new KeyEventSender();
				sender.execute(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
			}
		});

		findViewById(R.id.button_forward).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				KeyEventSender sender = new KeyEventSender();
				sender.execute(KeyEvent.KEYCODE_MEDIA_NEXT);
			}
		});

		findViewById(R.id.button_plus).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				KeyEventSender sender = new KeyEventSender();
				sender.execute(KeyEvent.KEYCODE_VOLUME_UP);
			}
		});

		findViewById(R.id.button_minus).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				KeyEventSender sender = new KeyEventSender();
				sender.execute(KeyEvent.KEYCODE_VOLUME_DOWN);
			}
		});
		
		findViewById(R.id.button_eject).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mGattServer != null && mDevice != null) {
					mGattServer.cancelConnection(mDevice);
				}
			}
		});
	}
}

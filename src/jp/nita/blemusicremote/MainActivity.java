package jp.nita.blemusicremote;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public class MainActivity extends Activity {

	public static final String SERVICE_UUID = "7865087B-D9D0-423A-9C80-042D9BBEA524";
	public static final String CHAR_UUID = "608072DD-6825-4293-B3E7-324CF0B5CA08";
	public static final String CHAR_CONFIG_UUID = "00002902-0000-1000-8000-00805f9b34fb";
	
	public static final int REQUEST_PLAYER = 1;
	public static final int REQUEST_CONTROLLER = 2;
	public static final int RESULT_OK = 0;
	public static final int RESULT_ERROR_BLUETOOTH_IS_OFF = 1;
	public static final int RESULT_ERROR_NOT_SUPPORTED = 2;
	public static final int RESULT_ERROR_FAILED = 2;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		findViewById(R.id.button_player).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
				startActivityForResult(intent, REQUEST_PLAYER);
			}

		});

		findViewById(R.id.button_controller).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent(MainActivity.this, ControllerActivity.class);
				startActivityForResult(intent, REQUEST_CONTROLLER);
			}

		});

		BluetoothManager bluetoothManager = (BluetoothManager) (this.getSystemService(Context.BLUETOOTH_SERVICE));
		BluetoothAdapter adapter = bluetoothManager.getAdapter();

		if ((adapter == null) || (!adapter.isEnabled())) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivity(enableBtIntent);
			return;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		// getMenuInflater().inflate(R.menu.main, menu);
		return false;
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
}

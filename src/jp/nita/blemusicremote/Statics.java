package jp.nita.blemusicremote;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface.OnClickListener;

public class Statics {

	public static ProgressDialog getProgressDialog(Context context,String title, String message) {
		ProgressDialog progressDialog;
		progressDialog = new ProgressDialog(context);
		progressDialog.setTitle(title);
		progressDialog.setMessage(message);
		progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		return progressDialog;
	}
	
	public static AlertDialog getAlertDialog(Context context,String title, String message, OnClickListener listener) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(title)
		       .setMessage(message)
		       .setPositiveButton(R.string.ok, listener);
		AlertDialog alertDialog = builder.create();
		return alertDialog;
	}
	
}

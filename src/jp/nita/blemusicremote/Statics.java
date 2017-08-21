package jp.nita.blemusicremote;

import android.app.ProgressDialog;
import android.content.Context;

public class Statics {

	public static ProgressDialog getProgressDialog(Context context,String title, String message) {
		ProgressDialog progressDialog;
		progressDialog = new ProgressDialog(context);
		progressDialog.setTitle(title);
		progressDialog.setMessage(message);
		progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		return progressDialog;
	}
	
}

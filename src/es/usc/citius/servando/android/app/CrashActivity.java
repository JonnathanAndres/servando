package es.usc.citius.servando.android.app;

import java.io.File;
import java.util.ArrayList;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;
import es.usc.citius.servando.android.ServandoPlatformFacade;
import es.usc.citius.servando.android.app.exception.AppExceptionHandler;
import es.usc.citius.servando.android.settings.StorageModule;

public class CrashActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_crash);

		findViewById(R.id.send_report_button).setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				sendMail(AppExceptionHandler.lastException);

			}
		});

		findViewById(R.id.cancel_report_button).setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				File trace = new File(StorageModule.getInstance().getPlatformLogsPath() + "/crash_trace.txt");
				if (trace.exists())
				{
					Log.d("CrashActivity", "Deleting crash trace: " + trace.getAbsolutePath().toString());
					trace.delete();
					finish();
				}
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.crash, menu);
		return true;
	}

	void sendMail(final Throwable exception)
	{
		try
		{
			File servandoLogFile = new File(StorageModule.getInstance().getPlatformLogsPath() + "/servando.log");
			File trace = new File(StorageModule.getInstance().getPlatformLogsPath() + "/crash_trace.txt");

			ArrayList<Uri> uris = new ArrayList<Uri>();
			uris.add(Uri.fromFile(trace));
			uris.add(Uri.fromFile(servandoLogFile));

			Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
			intent.setType("plain/text");
			intent.putExtra(Intent.EXTRA_EMAIL, new String[] { "servandoplatform@gmail.com" });
			intent.putExtra(Intent.EXTRA_SUBJECT, "[SERVANDO_CRASH_REPORT]");
			intent.putExtra(Intent.EXTRA_TEXT, "Patient ID: " + ServandoPlatformFacade.getInstance().getPatient().getName());

			intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

			Intent chooserIntent = Intent.createChooser(intent, "Send report");
			chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
			chooserIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

			startActivityForResult(chooserIntent, 1);

			findViewById(R.id.actions_layout).setVisibility(View.INVISIBLE);
			findViewById(R.id.done_button).setVisibility(View.VISIBLE);
			findViewById(R.id.done_button).setOnClickListener(new OnClickListener()
			{

				@Override
				public void onClick(View v)
				{
					deleteTraceAndExit();
				}
			});

		} catch (Exception e)
		{
			Log.e("AppExceptionHandler", "Uncaught error", e);
		}
	}

	private void deleteTraceAndExit()
	{
		File trace = new File(StorageModule.getInstance().getPlatformLogsPath() + "/crash_trace.txt");
		trace.delete();
		Toast.makeText(this, getString(R.string.report_thanks), Toast.LENGTH_SHORT).show();
		finish();
	}
}

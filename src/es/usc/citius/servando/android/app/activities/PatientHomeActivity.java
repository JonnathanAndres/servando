package es.usc.citius.servando.android.app.activities;

import java.text.SimpleDateFormat;

import org.joda.time.DateTime;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import es.usc.citius.servando.android.ServandoPlatformFacade;
import es.usc.citius.servando.android.advices.Advice;
import es.usc.citius.servando.android.advices.DailyReport;
import es.usc.citius.servando.android.agenda.ProtocolEngine;
import es.usc.citius.servando.android.agenda.ProtocolEngineListener;
import es.usc.citius.servando.android.app.R;
import es.usc.citius.servando.android.app.ServandoAdviceMgr;
import es.usc.citius.servando.android.app.ServandoAdviceMgr.AdviceListener;
import es.usc.citius.servando.android.app.uiHelper.AppManager;
import es.usc.citius.servando.android.logging.ILog;
import es.usc.citius.servando.android.logging.ServandoLoggerFactory;
import es.usc.citius.servando.android.models.protocol.MedicalActionExecution;
import es.usc.citius.servando.android.models.protocol.MedicalActionExecutionList;
import es.usc.citius.servando.android.models.services.IPlatformService;
import es.usc.citius.servando.android.ui.Iconnable;
import es.usc.citius.servando.android.ui.NotificationMgr;
import es.usc.citius.servando.android.ui.ServandoService;
import es.usc.citius.servando.android.util.UiUtils;

public class PatientHomeActivity extends Activity implements ProtocolEngineListener, AdviceListener {

	private static int MAX_MSG_SIZE = 50;
	private static int MAX_MSG_SIZE_NO_ACTIONS = 100;
	/**
	 * Servando paltform logger for this class
	 */
	private static final ILog log = ServandoLoggerFactory.getLogger(HomeActivity.class);
	private static final String DEBUG_TAG = PatientHomeActivity.class.getSimpleName();
	private static final int DOCTOR_DIALOG = 1;

	private ImageButton notificationsIcon;
	private TextView notificationCount;
	private NotificationsReceiver receiver;

	private TextView patientNameText;
	private TextView dayText;
	private TextView monthText;
	private TextView pendingActionsCountText;
	private LinearLayout centerRegion;
	private LinearLayout pendingActionsList;
	private RelativeLayout pendingLayout;

	private boolean hasFocus = false;

	private ImageButton coomunicationsButton;

	Handler h;

	private ProtocolEngine protocolEngine;

	MedicalActionExecutionList advised;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		log.debug("onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_patient_home);
		registerNotificationReceiver();
		h = new Handler();
		initComponents();
		protocolEngine = ServandoPlatformFacade.getInstance().getProtocolEngine();
		protocolEngine.addProtocolListener(this);
	}

	@Override
	protected void onNewIntent(Intent intent)
	{
		super.onNewIntent(intent);

		int removing = intent.getIntExtra("removing", -1);

		if (removing != -1)
		{
			int pendingCount = pendingActionsList.getChildCount();
			for (int i = 0; i < pendingCount; i++)
			{
				final View v = pendingActionsList.getChildAt(i);
				MedicalActionExecution e = (MedicalActionExecution) v.getTag();

				if (e != null && e.getUniqueId() == removing)
				{
					Animation fadeOut = getFadeOutAnimation();
					fadeOut.setAnimationListener(new AnimationListener()
					{

						@Override
						public void onAnimationStart(Animation animation)
						{

						}

						@Override
						public void onAnimationRepeat(Animation animation)
						{

						}

						@Override
						public void onAnimationEnd(Animation animation)
						{
							if (advised != null && advised.getExecutions().size() > 1)
							{
								v.setVisibility(View.INVISIBLE);
								LayoutParams lp = v.getLayoutParams();
								lp.width = 1;
								v.setLayoutParams(lp);
							} else
							{
								hidePendingActionsView();
							}
						}
					});

					v.startAnimation(fadeOut);
				}
			}
		}
	}

	private Animation getFadeOutAnimation()
	{
		Animation fadeOut = new AlphaAnimation(1, 0);
		fadeOut.setDuration(1000);
		fadeOut.setFillAfter(true);
		return fadeOut;
	}

	private Animation getFadeInAnimation()
	{
		Animation fadeOut = new AlphaAnimation(0, 1);
		fadeOut.setDuration(1000);
		return fadeOut;
	}

	private void initComponents()
	{
		DateTime now = DateTime.now();

		notificationsIcon = (ImageButton) findViewById(R.id.home_notifications_icon);
		notificationCount = (TextView) findViewById(R.id.home_notifications_count);
		patientNameText = (TextView) findViewById(R.id.patient_name_tv);
		dayText = (TextView) findViewById(R.id.patient_day_tv);
		monthText = (TextView) findViewById(R.id.patient_month_tv);
		pendingActionsCountText = (TextView) findViewById(R.id.patient_pending_actions_count);
		pendingLayout = (RelativeLayout) findViewById(R.id.PendingLayout);
		pendingActionsList = (LinearLayout) findViewById(R.id.pending_actions);
		centerRegion = (LinearLayout) findViewById(R.id.center_region);
		pendingLayout.setVisibility(View.INVISIBLE);

		coomunicationsButton = (ImageButton) findViewById(R.id.bb_comunication);

		// Initialize values
		patientNameText.setText(ServandoPlatformFacade.getInstance().getPatient().getName());
		dayText.setText("" + now.getDayOfMonth());
		monthText.setText("" + now.toString("MMM"));

		coomunicationsButton.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				startCommunications();
			}
		});
	}

	private void addToCenter(View v, boolean removeAll)
	{
		if (removeAll)
			centerRegion.removeAllViews();

		centerRegion.setVisibility(View.INVISIBLE);
		centerRegion.addView(v);

		AnimationSet set = new AnimationSet(true);
		Animation animation = new AlphaAnimation(0.0f, 1.0f);
		animation.setDuration(600);
		set.addAnimation(animation);
		invalidateFullView();

		centerRegion.startAnimation(animation);
		centerRegion.setVisibility(View.VISIBLE);
	}

	// private void addPendingActionLauncherTest()
	// {
	//
	// int pixh = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 50, getResources().getDisplayMetrics());
	// int pixw = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 60, getResources().getDisplayMetrics());
	// LayoutParams params = new LayoutParams(pixw, pixh);
	// ImageButton b = (ImageButton) getLayoutInflater().inflate(R.layout.pending_action_launcher, null);
	// pendingActionsList.addView(b, params);
	// invalidateFullView();
	//
	// }

	void invalidateFullView()
	{
		ViewGroup vg = (ViewGroup) findViewById(R.id.patient_home_view);
		vg.invalidate();
	}

	private void toast(String text)
	{
		Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus)
	{
		// if (hasFocus)
		// {
		// ServandoPlatformFacade.get
		// }else {
		//
		// }
		// this.hasFocus = hasFocus;
	}

	private void registerNotificationReceiver()
	{
		IntentFilter notificationFIlter = new IntentFilter(ServandoService.NOTIFICATIONS_UPDATE);
		receiver = new NotificationsReceiver();
		LocalBroadcastManager.getInstance(this).registerReceiver(receiver, notificationFIlter);
	}

	private void updateNotifications()
	{

		int count = NotificationMgr.getInstance().getCount();

		if (count > 0)
		{
			notificationCount.setVisibility(View.VISIBLE);
			notificationsIcon.setVisibility(View.VISIBLE);
			notificationCount.setText("" + count);
		} else
		{
			notificationCount.setVisibility(View.INVISIBLE);
			notificationsIcon.setVisibility(View.INVISIBLE);
			notificationCount.setText("0");
		}
	}

	@Override
	protected void onDestroy()
	{
		ServandoPlatformFacade.getInstance().getProtocolEngine().removeProtocolListener(this);
		super.onDestroy();
	}

	@Override
	protected void onPause()
	{
		hasFocus = false;
		ServandoAdviceMgr.getInstance().removeAdviceListener(this);
		if (protocolEngine != null && protocolEngine.getAdvisedActions().getExecutions().size() > 0)
		{
			// ServandoService.updateServandoNotification(PatientHomeActivity.this, true, false, " ");
		}
		super.onPause();
	}

	@Override
	protected void onRestart()
	{
		super.onRestart();
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		hasFocus = true;
		ServandoAdviceMgr.getInstance().addAdviceListener(this);
		log.debug("onResume");
		updatePendingActions();
		showHomeAdvice();
	}

	@Override
	protected void onStart()
	{
		super.onStart();
	}

	@Override
	protected void onStop()
	{
		super.onStop();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.patient_home_menu, menu);
		return true;
	}

	public void onClickAbout(View v)
	{
		Intent intent = new Intent(getApplicationContext(), AboutActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
	}

	public void onClickAgenda(View v)
	{
		Intent intent = new Intent(getApplicationContext(), AgendaActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
	}

	public void onClickNotifications(View v)
	{
		Intent intent = new Intent(getApplicationContext(), NotificationsActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
	}

	private void startCommunications()
	{
		Intent intent = new Intent(getApplicationContext(), AdvicesListActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{

		int id = item.getItemId();

		// Handle item selection
		if (id == R.id.menu_settings)
		{
			Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);

		} else if (id == R.id.menu_help)
		{
			UiUtils.showToast("Help selected", this);
		} else if (id == R.id.menu_close)
		{
			exit();
		} else if (id == R.id.menu_doctor_view)
		{
			showDialog(DOCTOR_DIALOG);

		} else
		{
			UiUtils.showToast("Unknown option", this);
			return super.onOptionsItemSelected(item);
		}
		return true;
	}

	private void showDoctorHome()
	{
		Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
	}

	private void exit()
	{
		AppManager.closeApplication(this);
		finish();
	}

	/**
	 * Build a button
	 * 
	 * @param text
	 * @param resId
	 * @param listener
	 * @return
	 */
	public Button buildButton(String text, int resId, OnClickListener listener)
	{
		Button b = new Button(this, null, R.style.HomeButton);
		b.setText(text);
		b.setOnClickListener(listener);
		return b;
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
	}

	@Override
	protected Dialog onCreateDialog(int id)
	{
		if (id == DOCTOR_DIALOG)
		{
			// Set an EditText view to get user input
			final EditText input = new EditText(this);

			AlertDialog.Builder builder = new AlertDialog.Builder(this);

			builder.setView(input);

			builder.setMessage("Enter your password")

			.setPositiveButton("Done", new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int id)
				{
					Log.d(DEBUG_TAG, "Input text: " + input.getText());
					// if (input.getText().equals("admin"))
					// {
					showDoctorHome();
					// }
					dialog.dismiss();
				}
			}).setNegativeButton("Cancel", new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int id)
				{
					dialog.dismiss();
				}
			});
			// Create the AlertDialog object and return it
			return builder.create();
		}
		return super.onCreateDialog(id);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		if (keyCode == KeyEvent.KEYCODE_BACK)
		{
			finish();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	public class NotificationsReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent)
		{
			updateNotifications();
		}
	}

	private void updatePendingActions()
	{
		h.post(new Runnable()
		{
			@Override
			public void run()
			{
				new AsyncTask<String, Integer, String>()
				{
					@Override
					protected String doInBackground(String... params)
					{

						// this call can be blocking, so we must do it asynchronously
						if (protocolEngine != null)
							advised = protocolEngine.getAdvisedActions();
						return null;
					}

					@Override
					protected void onPostExecute(String result)
					{

						if (protocolEngine != null && advised != null)
						{
							pendingActionsCountText.setText("" + advised.getExecutions().size());

							if (advised.getExecutions().size() > 0)
							{
								showPendingActionsView();
								pendingActionsList.removeAllViews();
								for (MedicalActionExecution m : advised.getExecutions())
								{
									log.debug("Adding launcher for action" + m.getUniqueId());
									addPendingActionLauncher(m);
								}
								invalidateFullView();

							} else
							{
								hidePendingActionsView();
								invalidateFullView();
							}
							log.debug("Pending actions list updated (" + advised.getExecutions().size() + ")");

							ServandoService.updateServandoNotification(PatientHomeActivity.this, false, false, " ");
						}
					}
				}.execute();
			}
		});

	}

	private void logTrace()
	{
		try
		{
			// throw new Exception();
		} catch (Exception e)
		{
			log.error("GENERATED EXCEPTION", e);
		}

	}

	private void hidePendingActionsView()
	{
		if (pendingLayout.getVisibility() == View.VISIBLE)
		{

			Animation fadeOut = getFadeOutAnimation();

			fadeOut.setAnimationListener(new AnimationListener()
			{

				@Override
				public void onAnimationStart(Animation animation)
				{
					// TODO Auto-generated method stub

				}

				@Override
				public void onAnimationRepeat(Animation animation)
				{
					// TODO Auto-generated method stub

				}

				@Override
				public void onAnimationEnd(Animation animation)
				{
					pendingLayout.setVisibility(View.INVISIBLE);
				}
			});

			pendingLayout.startAnimation(fadeOut);

		}

	}

	private void showPendingActionsView()
	{
		if (pendingLayout.getVisibility() == View.INVISIBLE)
		{
			log.debug("showPendingActionsView");
			AnimationSet set = new AnimationSet(true);
			Animation animation = new AlphaAnimation(0.0f, 1.0f);
			animation.setDuration(500);
			set.addAnimation(animation);
			animation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
					Animation.RELATIVE_TO_SELF, 0.0f);
			animation.setDuration(500);
			set.addAnimation(animation);
			pendingLayout.startAnimation(animation);
			pendingLayout.setVisibility(View.VISIBLE);
		}
	}

	void updatePendingActionsCountText(int count)
	{
		pendingActionsCountText.setText(count + "");
	}

	private void addPendingActionLauncher(MedicalActionExecution m)
	{

		if (pendingLayout.getVisibility() == View.INVISIBLE)
		{
			pendingLayout.setVisibility(View.VISIBLE);
		}

		int pixh = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 50, getResources().getDisplayMetrics());
		int pixw = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 60, getResources().getDisplayMetrics());

		IPlatformService provider = m.getAction().getProvider();

		LayoutParams params = new LayoutParams(pixw, pixh);

		ImageButton b = (ImageButton) getLayoutInflater().inflate(R.layout.pending_action_launcher, null);
		b.setId(m.getUniqueId());
		b.setTag(m);
		b.setImageDrawable(getResources().getDrawable(((Iconnable) provider).getIconResourceId()));
		b.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				MedicalActionExecution exec = ((MedicalActionExecution) v.getTag());
				log.debug(exec.getAction().getDisplayName() + " clicked");
				showMedicalActionActivity(exec.getUniqueId());
			}
		});

		pendingActionsList.addView(b, params);
		log.debug("Adding view... " + m.getUniqueId());
	}

	private void showMedicalActionActivity(int actionId)
	{
		Intent intent = new Intent(getApplicationContext(), SwitcherActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.putExtra("action_id", actionId);
		startActivity(intent);
	}

	@Override
	public void onExecutionStart(MedicalActionExecution target)
	{
		if (hasFocus)
		{
			updatePendingActions();
		}
	}

	@Override
	public void onExecutionAbort(MedicalActionExecution target)
	{
		if (hasFocus)
		{
			updatePendingActions();
		}
	}

	@Override
	public void onExecutionFinish(MedicalActionExecution target)
	{
		if (hasFocus)
		{
			updatePendingActions();
		}
	}

	@Override
	public void onLoadDayActions()
	{
		// updatePendingActionsOnEvent();
	}

	@Override
	public void onProtocolChanged()
	{
		// updatePendingActionsOnEvent();

	}

	private View getAdviceView(Advice advice)
	{

		SimpleDateFormat sdf = new SimpleDateFormat("EEE dd, HH:mm");
		LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View v = vi.inflate(R.layout.home_advice, null);

		TextView from = (TextView) v.findViewById(R.id.message_intro);
		TextView msg = (TextView) v.findViewById(R.id.message_text);
		TextView when = (TextView) v.findViewById(R.id.message_time);

		DisplayMetrics dm = new DisplayMetrics();
		this.getWindowManager().getDefaultDisplay().getMetrics(dm);

		int ellipsizeSize = pendingLayout.getVisibility() == View.VISIBLE ? MAX_MSG_SIZE : MAX_MSG_SIZE_NO_ACTIONS;

		String formattedMsg = advice.getMsg().length() < ellipsizeSize ? advice.getMsg() : (advice.getMsg().substring(0, ellipsizeSize) + "...");

		from.setText(advice.getSender() + ":");
		msg.setText(formattedMsg);
		when.setText(sdf.format(advice.getDate()));
		return v;
	}

	public void onClickHomeMessage(View v)
	{
		Advice a = ServandoAdviceMgr.getInstance().getHomeAdvice();

		if (a != null)
		{
			if (DailyReport.getInstance().getNotSeen().size() == 1 && a.getId() == DailyReport.getInstance().getNotSeen().get(0).getId())
			{
				hideHomeAdvice();

			} else
			{
				startCommunications();
			}
		}

		ServandoAdviceMgr.getInstance().getHomeAdvice().setSeen(true);

	}

	private void showHomeAdvice()
	{
		hideHomeAdvice();

		Advice advice = ServandoAdviceMgr.getInstance().getHomeAdvice();

		if (advice != null && !advice.isSeen())
		{
			addToCenter(getAdviceView(advice), true);
		}
	}

	private void hideHomeAdvice()
	{
		centerRegion.removeAllViews();
	}

	@Override
	public void onAdvice(final Advice advice)
	{
		runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				centerRegion.removeAllViews();

				if (advice != null && !advice.isSeen())
				{
					addToCenter(getAdviceView(advice), true);
				}
			}
		});
	}

}

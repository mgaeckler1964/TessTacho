package at.gaeckler.TessTacho;

import java.text.DecimalFormat;

import at.gaeckler.TessTacho.R;
import at.gaeckler.gps.GpsActivity;
import at.gaeckler.gps.GpsProcessor;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

public class TessTachoActivity extends GpsActivity
{
	private TextView				m_statusLabel = null;
	private TextView				m_brakeStatusLabel = null;
	private TextView				m_accelStatusLabel = null;
	private TachoWidget				m_theTacho = null;
	private String					m_myStatus = "Willkommen";
	private long					m_locationFixCount = 0;
	private LocationManager			m_locationManager;
	private Location				m_distanceLocation = null;

	private Location				m_brakeLocation = null;
	private double					m_brakeDistance = 0.0;
	private long					m_brakeTime = 0;
	private long					m_brakeLocationCount =0;
	private boolean					m_inbrake = false;
	
	private Location				m_accelLocation = null;
	private double					m_accelDistance = 0.0;
	private long					m_accelTime = 0;
	private long					m_accelLocationCount =0;
	private boolean					m_inaccel = false;
	
	double							m_startSpeed = 0;	// default access
	double							m_targetSpeed = 0;	// default access
	private double 					m_maxAccel = 0;
	private double 					m_maxBrake = 0;
	private double					m_totalDistance = 0.0;
	private double					m_dayDistance = 0.0;
	private double					m_accuracy = 0.0;
	private PowerManager.WakeLock	m_wakeLock;

	private static final DecimalFormat	s_accuracyFormat = new DecimalFormat( "Genauigkeit: 0.000m" );
	private static final String			CONFIGURATION = "TessTacho.cfg";
	
	public  static final String			MAX_SPEED_KEY = "maxSpeed"; 
	public  static final String			START_SPEED_KEY = "startSpeed"; 
	public  static final String			TARGET_SPEED_KEY = "targetSpeed"; 
	public  static final String			MAX_ACCEL_KEY = "maxAccel"; 
	public  static final String			ACCEL_STATUS_KEY = "accelStatus";

	public  static final String			MAX_BRAKE_KEY = "maxBrake"; 
	public  static final String			BRAKE_SPEED_KEY = "brakeSpeed"; 
	public  static final String			BRAKE_DISTANCE_KEY = "brakeDistance";
	public  static final String			BRAKE_LON_KEY = "brakeLon"; 
	public  static final String			BRAKE_LAT_KEY = "brakeLat"; 
	public  static final String			BRAKE_PROV_KEY = "brakeProvider";
	public  static final String			BRAKE_STATUS_KEY = "brakeStatus";

	public  static final String			MAX_TACHO_SPEED_KEY = "maxTachoSpeed"; 
	public  static final String			RESOLUTION_KEY = "gpsSpeedResolution";
	
	private static final String			DAY_DISTANCE_KEY = "dayDistance";
	private static final String			TOTAL_DISTANCE_KEY = "totalDistance";
	private static final String			LOCATION_FIX_COUNT_KEY = "locationFixCount";

    private void showMessage( String title, String message, final boolean terminate )
    {
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setMessage(message)
    		   .setTitle(title)
    	       .setCancelable(false)
    	       .setNegativeButton("Fertig", new DialogInterface.OnClickListener() {
    	           public void onClick(DialogInterface dialog, int id) {
    	                dialog.cancel();
    	                if( terminate )
    	                {
    	                	finish();
    	                }
    	           }
    	       })
    	       .setIcon(R.drawable.icon);
    	AlertDialog alert = builder.create();
    	alert.show();
    }

	/** Called when the activity is first created. */
	@Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        if( checkCallingOrSelfPermission("android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_DENIED )
        {
        	showMessage("TessTacho", "Berechtigung für Standort fehlt!", true);
        	return;
        }
        
        PowerManager	pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        m_wakeLock = pm.newWakeLock( PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "TessTacho" );
        m_wakeLock.acquire();
        getWindow().addFlags(
        	WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|
        	WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON|
        	WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );
        
        setContentView(R.layout.main);

        m_statusLabel = (TextView)findViewById( R.id.statusLabel );
    	setStatus( m_myStatus );
        m_theTacho = (TachoWidget)findViewById( R.id.myTacho );

        m_brakeStatusLabel = (TextView)findViewById( R.id.brakeStatus );
        m_accelStatusLabel = (TextView)findViewById( R.id.accelStatus );

        if( savedInstanceState != null )
        {
        	m_totalDistance = savedInstanceState.getDouble(TOTAL_DISTANCE_KEY); 
        	m_startSpeed = savedInstanceState.getDouble(START_SPEED_KEY); 
        	m_targetSpeed = savedInstanceState.getDouble(TARGET_SPEED_KEY); 
        	
        	m_dayDistance = savedInstanceState.getDouble(DAY_DISTANCE_KEY);
           	m_theTacho.setMaxTachoSpeed(savedInstanceState.getDouble(MAX_TACHO_SPEED_KEY));
           	
           	m_theTacho.setMaxSpeed(savedInstanceState.getDouble(MAX_SPEED_KEY));
           	m_maxBrake = savedInstanceState.getDouble(MAX_BRAKE_KEY);
           	m_maxAccel = savedInstanceState.getDouble(MAX_ACCEL_KEY);
           	
           	String provider = savedInstanceState.getString(BRAKE_PROV_KEY);
           	if( provider != null )
           	{
           		m_brakeLocation = new Location(provider);
           		m_brakeLocation.setLongitude(savedInstanceState.getDouble(BRAKE_LON_KEY));
           		m_brakeLocation.setLatitude(savedInstanceState.getDouble(BRAKE_LAT_KEY));
           		m_brakeLocation.setSpeed((float) savedInstanceState.getDouble(BRAKE_SPEED_KEY));
           	}
           	m_brakeDistance = savedInstanceState.getDouble(BRAKE_DISTANCE_KEY); 

           	m_locationFixCount = savedInstanceState.getLong(LOCATION_FIX_COUNT_KEY);
           	
           	m_brakeStatusLabel.setText(savedInstanceState.getString(BRAKE_STATUS_KEY));
           	m_accelStatusLabel.setText(savedInstanceState.getString(ACCEL_STATUS_KEY));
        }
        else
        {
        	loadPreferences();
        }

        // Acquire a reference to the system Location Manager
        m_locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        createGpsTimer(FAST_GPS);
        showSpeed( 0, 0 );
	}

    @Override
    public boolean onCreateOptionsMenu( android.view.Menu menu )
    {
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.tt_menu, menu);

    	return super.onCreateOptionsMenu(menu);
    }
    private void configAccelTest()
    {
    	LayoutInflater layoutInflater = getLayoutInflater();
    	final View view = layoutInflater.inflate(R.layout.accel_test, null);
    	final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
    	alertDialog.setTitle("Beschleunigungstest");
    	alertDialog.setIcon(R.drawable.icon);
    	alertDialog.setCancelable(false);
    	alertDialog.setMessage("Geben Sie hier die Start- und Zielgeschwindigkeit ein:");


    	final EditText startSpeed = (EditText) view.findViewById(R.id.startSpeed);
    	final EditText targetSpeed = (EditText) view.findViewById(R.id.targetSpeed);
    	if (m_startSpeed > 0)
    	{
    		startSpeed.setText(Long.toString(GpsProcessor.speedToKmh(m_startSpeed)));
    	}
    	if (m_targetSpeed > 0)
    	{
    		targetSpeed.setText(Long.toString(GpsProcessor.speedToKmh(m_targetSpeed)));
    	}
    	alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
    	    @Override
    	    public void onClick(DialogInterface dialog, int which) {

    			try
    			{
    				m_startSpeed = GpsProcessor.speedToMs((long)(Double.parseDouble(startSpeed.getText().toString())+0.5));
    				m_targetSpeed = GpsProcessor.speedToMs((long)(Double.parseDouble(targetSpeed.getText().toString())+0.5));
        	        alertDialog.dismiss();
    			}
    			catch (NumberFormatException e)
    			{
    				// ignore, don't change speed
    			}
    	    }
    	});


    	alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Abbruch", new DialogInterface.OnClickListener() {
    	    @Override
    	    public void onClick(DialogInterface dialog, int which) {
    	        alertDialog.dismiss();
    	    }
    	});

    	       
    	alertDialog.setView(view);
    	alertDialog.show();    	
    }

    @Override
    public boolean onOptionsItemSelected( MenuItem item )
    {
    	System.out.println("onOptionsItemSelected " +item.toString());
    	int	itemId = item.getItemId();
    	switch( itemId )
    	{
    	case R.id.exit:
    		finish();
            break;
    	case R.id.accelTest:
    		configAccelTest();
    		break;
    	case R.id.statistics:
    	{
        	Intent intent = new Intent( this, StatScreen.class );
        	intent.putExtra(MAX_SPEED_KEY, m_theTacho.getMaxSpeed());
        	intent.putExtra(MAX_ACCEL_KEY, m_maxAccel);
        	intent.putExtra(MAX_BRAKE_KEY, m_maxBrake);
        	if( m_brakeLocation != null )
        	{
            	intent.putExtra(BRAKE_SPEED_KEY, m_brakeLocation.getSpeed());
            	intent.putExtra(BRAKE_DISTANCE_KEY, m_brakeDistance);
        	}
        	intent.putExtra(RESOLUTION_KEY, getResolution());
        	startActivity( intent );
    		break;
    	}
    	case R.id.about:
    	{
    		String name = getString(R.string.app_name);
    		String version = getString(R.string.app_version);
    		showMessage(
    			name, 
    			name + " "+version+"\nVon Martin für Tess.\n(c) 2013-2025 by Martin Gäckler\nhttps://www.gaeckler.at/",
    			false
    		);
    		break;
    	}
    	}

    	return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onOptionsMenuClosed(Menu menu) {
        super.onOptionsMenuClosed(menu);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.CUR_DEVELOPMENT) {
            // Workaround for https://issuetracker.google.com/issues/315761686
            invalidateOptionsMenu();
        }
    }
    
    private void savePreferences()
    {
    	SharedPreferences settings = getSharedPreferences(CONFIGURATION, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putFloat( TOTAL_DISTANCE_KEY, (float)m_totalDistance );
        editor.putFloat( START_SPEED_KEY, (float)m_startSpeed );
        editor.putFloat( TARGET_SPEED_KEY, (float)m_targetSpeed );
        editor.commit();
    }

    private void loadPreferences()
    {
    	SharedPreferences settings = getSharedPreferences(CONFIGURATION, 0);
    	m_totalDistance = settings.getFloat(TOTAL_DISTANCE_KEY, 0);
    	m_startSpeed = settings.getFloat(START_SPEED_KEY,0); 
    	m_targetSpeed = settings.getFloat(TARGET_SPEED_KEY,0); 
    }

    @Override
    public void onPause()
    {
        m_wakeLock.release();
        savePreferences();
        super.onPause();
    }
    
	@Override
	public void onDestroy()
	{
		savePreferences();
        super.onDestroy();
    }
	@Override
	protected void  onSaveInstanceState (Bundle outState)
	{
		outState.putDouble(TOTAL_DISTANCE_KEY, m_totalDistance );
		outState.putDouble(DAY_DISTANCE_KEY, m_dayDistance );
		outState.putDouble(MAX_ACCEL_KEY, m_maxAccel);
		outState.putDouble(MAX_BRAKE_KEY, m_maxBrake);
		outState.putDouble(MAX_TACHO_SPEED_KEY, m_theTacho.getMaxTachoSpeed());
		outState.putDouble(MAX_SPEED_KEY, m_theTacho.getMaxSpeed());
		outState.putDouble(START_SPEED_KEY, m_startSpeed);
		outState.putDouble(TARGET_SPEED_KEY, m_targetSpeed);
		outState.putLong(LOCATION_FIX_COUNT_KEY, m_locationFixCount);

		if (m_brakeLocation!=null)
		{
			outState.putDouble(BRAKE_SPEED_KEY, m_brakeLocation.getSpeed());
			outState.putDouble(BRAKE_LON_KEY, m_brakeLocation.getLongitude());
			outState.putDouble(BRAKE_LAT_KEY, m_brakeLocation.getLatitude());
			outState.putString(BRAKE_PROV_KEY, m_brakeLocation.getProvider());
		}
		else
		{
			outState.remove(BRAKE_PROV_KEY);
		}
		outState.putDouble(BRAKE_DISTANCE_KEY, m_brakeDistance);

		outState.putString(BRAKE_STATUS_KEY, m_brakeStatusLabel.getText().toString());
		outState.putString(ACCEL_STATUS_KEY, m_accelStatusLabel.getText().toString());
	}
	
	@Override
	public void onLocationChanged( Location newLocation )
    {
    	double	distance;
    	
    	++m_locationFixCount;
    	m_accuracy = newLocation.getAccuracy();
    	setStatus( m_myStatus );
    	
		if( m_distanceLocation != null )
		{
			distance = m_distanceLocation.distanceTo(newLocation);
			m_totalDistance += distance;
			m_dayDistance += distance;
		}
		else
		{
			distance = 0;
		}

		double speed = getSpeed();
		double accel = getAccel();
		showSpeed( GpsProcessor.speedToKmh(speed), accel );
    	
    	m_distanceLocation = newLocation;

    	if( accel<0 )
		{
			if(m_brakeLocation==null)
			{
				m_brakeLocation = newLocation;
				m_brakeDistance = 0;
				m_brakeTime = 0;
				m_brakeLocationCount = 0;
				m_inbrake = true;
			}
			else if( m_inbrake )
			{
				
				m_brakeDistance += distance;
				m_brakeTime = newLocation.getTime() - m_brakeLocation.getTime();
				++m_brakeLocationCount;
			}
		}
		else if (speed==0)
		{
			m_inbrake = false;
		}
		else if(accel >=0 && speed > 5)
		{
			m_brakeLocation = null;
		}
    	if(m_brakeLocation!=null)
    	{
    		double brakeSpeed = m_brakeLocation.getSpeed();
	        String brakeSpeedStr = TachoWidget.s_speedFormat.format(GpsProcessor.speedToKmh(brakeSpeed));
	        String brakeDistanceStr = TachoWidget.s_dayDistanceFormat.format(m_brakeDistance);
	        String brakeTimeStr = Long.toString(m_brakeTime);
	        String brakeCountStr = Long.toString(m_brakeLocationCount);
	        
	        m_brakeStatusLabel.setText(brakeSpeedStr + '/' + brakeDistanceStr + "m/" + brakeTimeStr + "ms/" + brakeCountStr);
    	}
    	
    	if( speed>m_startSpeed )
    	{
    		if(m_accelLocation==null)
    		{
    			m_accelLocation = newLocation;
    			m_accelDistance = 0;
    			m_accelTime = 0;
    			m_accelLocationCount = 0;
    			m_inaccel = true;
    		}
    		else if( m_inaccel )
    		{
				m_accelDistance += distance;
				m_accelTime = newLocation.getTime() - m_accelLocation.getTime();
				++m_accelLocationCount;
    		}
    	}
    	else if(speed == 0)
    	{
    		m_accelLocation=null;
    	}
    	if(m_accelLocation!=null && m_inaccel)
    	{
	        String accelDistanceStr = TachoWidget.s_dayDistanceFormat.format(m_accelDistance);
	        String accelTimeStr = Long.toString(m_accelTime);
	        String accelCountStr = Long.toString(m_accelLocationCount);

	        m_accelStatusLabel.setText(accelTimeStr + "ms/" + accelDistanceStr + "m/" + accelCountStr);
    		if (speed>=m_targetSpeed)
    		{
    			m_inaccel = false;
    		}
    	}
    }

    void showSpeed( double speed, double accel )
    {
		if( accel > m_maxAccel)
			m_maxAccel = accel;
		else if( accel < m_maxBrake)
			m_maxBrake = accel;

		double displayedDay = m_dayDistance > 1000 ? m_dayDistance/1000.0 : m_dayDistance; 
    	m_theTacho.showSpeedAndDistance(speed, accel, (int)(m_totalDistance/1000), displayedDay, m_brakeLocation!=null);
    }
    void setStatus( String text )
    {
    	m_myStatus = text;
    	m_statusLabel.setText( text + " " + s_accuracyFormat.format(m_accuracy) + " " + Long.toString(m_locationFixCount) );
    }

	@Override
	public void onLocationEnabled() {
    	setStatus( "GPS ist eingeschaltet");
	}
	@Override
	public void onLocationDisabled() {
    	setStatus( "GPS ist abgeschaltet");
		showSpeed( 0, 0 );
	}
	@Override
	public void onLocationServiceOn() {
		setStatus( "GPS Empfang" );
	}
	@Override
	public void onLocationServiceOff() {
		setStatus( "Kein GPS Empfang" );
		showSpeed( 0, 0 );
	}
	@Override
	public void onLocationTempOff() {
		setStatus( "Kurzfristig kein GPS Empfang" );
	}
	@Override
	public void onGpsStatusChanged2(int event) {
		if( event == GpsStatus.GPS_EVENT_STARTED )
        	setStatus( "GPS gestartet");
		else if( event == GpsStatus.GPS_EVENT_STOPPED )
        	setStatus( "GPS gestoppt");
		else if( event == GpsStatus.GPS_EVENT_FIRST_FIX )
        	setStatus( "GPS erster Fix");
		else if( event == GpsStatus.GPS_EVENT_SATELLITE_STATUS  )
		{
			int Satellites = 0;
			int SatellitesInFix = 0;
			for (GpsSatellite sat : m_locationManager.getGpsStatus(null).getSatellites())
			{
				if(sat.usedInFix())
					SatellitesInFix++;              

				Satellites++;
			}
			setStatus( "GPS Satelliten: " + Integer.toString(SatellitesInFix) + "/" + Integer.toString(Satellites) );
		}
	}

	@Override
	public void onPermissionError() {
    	showMessage("TessTacho", "Berechtigung für Standort fehlt!", true);
	}
}
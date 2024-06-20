package at.gaeckler.gps;

import java.util.concurrent.locks.ReentrantLock;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.CountDownTimer;

public abstract class GpsActivity extends Activity {

	public static final int AUTO_GPS = 0;
	public static final int FAST_GPS = 100;
	public static final int NORMAL_GPS = 1000;
	public static final int SLOW_GPS = 10000;
	
	CountDownTimer		m_gpsTimer = null;
	LocationManager		m_locationManager = null;
	private LocationListener	m_locationListener = null;
	private GpsStatus.Listener	m_gpsStatusListener = null;
	private final GpsProcessor	m_processor = new GpsProcessor();
	private int m_gpsInterval = 0;

	public abstract void onLocationEnabled();
	public abstract void onLocationDisabled();
	public abstract void onLocationServiceOn();
	public abstract void onLocationServiceOff();
	public abstract void onLocationTempOff();
	public abstract void onGpsStatusChanged2(int event);
	public abstract void onLocationChanged( Location newLocation );
	public abstract void onPermissionError();
	
    /** Called when the activity is first created. */
	@Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        if( checkCallingOrSelfPermission("android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_DENIED )
        {
        	onPermissionError();
        	return;
        }

        // Acquire a reference to the system Location Manager
        m_locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // Define a listener that responds to location updates
        m_locationListener = new LocationListener()
        {
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) 
            {
            	if( status == LocationProvider.OUT_OF_SERVICE )
            	{
            		onLocationServiceOff();
            	}
            	else if( status == LocationProvider.TEMPORARILY_UNAVAILABLE )
            	{
            		onLocationTempOff();
            	}
            	else if( status == LocationProvider.AVAILABLE )
            	{
            		onLocationServiceOn();
            	}
            }

            @Override
            public void onProviderEnabled(String provider)
            {
            	onLocationEnabled();
            }

            @Override
            public void onProviderDisabled(String provider)
            {
            	onLocationDisabled();
            }

			@Override
			public void onLocationChanged(Location location)
			{
				lockLocationChanged( location );
			}
        };

        m_gpsStatusListener = new GpsStatus.Listener()
        {

			@Override
			public void onGpsStatusChanged(int event)
			{
				onGpsStatusChanged2(event);
			}
        };

        System.out.println("addGpsStatusListener");
        m_locationManager.addGpsStatusListener(m_gpsStatusListener);

        
        // Register the listener with the Location Manager to receive location updates
	    m_locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 50, (float) 0.1, m_locationListener);
	    m_locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 50, (float) 0.1, m_locationListener);

	    createGpsTimer(NORMAL_GPS);
    }
	
	public void createGpsTimer( int interval )
	{
		if (m_gpsTimer!=null)
		{
			m_gpsTimer.cancel();
		}
		if( interval > 0 )
		{
			m_gpsInterval = interval;
		    m_gpsTimer = new CountDownTimer(100000000, interval) {
		    	
		    	private Location m_lastKnown=null;
		
		    	@Override
		    	public void onTick(long millisUntilFinished) {
		    		Location newLocation = m_locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		    		if (newLocation != null && (m_lastKnown==null || !m_lastKnown.equals(newLocation)))
		    		{
		    			lockLocationChanged(newLocation);
		    		}
		    	}
			
		    	@Override
		    	public void onFinish() {
		    		m_gpsTimer.start();
		    	}
			}.start();
		}
		else
		{
			m_gpsTimer = null;
			m_gpsInterval = 0;
		}
	}
	public void removeGpsTimer()
	{
		if (m_gpsTimer!=null)
		{
			m_gpsTimer.cancel();
			m_gpsTimer = null;
			m_gpsInterval = 0;
		}
	}
	public int getInterval( )
	{
		return m_gpsInterval;
	}
	private final ReentrantLock m_lock = new ReentrantLock();
	private Location m_lastLocation = null;
	void lockLocationChanged( Location newLocation )
    {
		m_lock.lock();
		try {
			if (m_lastLocation == null || 
				newLocation.getTime() != m_lastLocation.getTime() || 
				newLocation.getAltitude() != m_lastLocation.getAltitude() ||
				newLocation.getLongitude() != m_lastLocation.getLongitude() ||
				newLocation.getLatitude() != m_lastLocation.getLatitude() )
			{
				m_lastLocation = newLocation;
				if( m_processor.onLocationChanged(newLocation) )
			    {
					onLocationChanged( newLocation );
			    }
			}
		} finally {
			m_lock.unlock();
		}
    }

	public Iterable<GpsSatellite> getSatellites()
	{
		return m_locationManager.getGpsStatus(null).getSatellites();
	}
	
	@Override
	public void onDestroy()
	{
        // Acquire a reference to the system Location Manager
        // LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

		m_locationManager.removeUpdates( m_locationListener );
		m_locationManager.removeGpsStatusListener( m_gpsStatusListener );
        
        super.onDestroy();
    }
	
	public boolean getHasLocation()
	{
		return m_processor.hasLocation();
	}
	
	public Location getLastLocation()
	{
		return m_processor.lastLocation();
	}
	
	public double getAccuracy()
	{
		return m_processor.getAccuracy();
	}
	
	public int getNumLocations()
	{
		return m_processor.getNumLocations();
	}
	
	public double getCurBearing()
	{
		return m_processor.getCurBearing();
	}
	public double getSpeed()
	{
		return m_processor.getSpeed();
	}
	public double getAccel()
	{
		return m_processor.getAccel();
	}
	public double getResolution()
	{
		return m_processor.getResolution();
	}
}

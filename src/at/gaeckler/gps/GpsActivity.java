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

	CountDownTimer		m_gpsTimer = null;
	LocationManager		m_locationManager = null;
	private LocationListener	m_locationListener = null;
	private GpsStatus.Listener	m_gpsStatusListener = null;
	private final GpsProcessor	m_processor = new GpsProcessor();

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

	    createGpsTimer(1000);
    }
	
	public void createGpsTimer( int interval )
	{
		if (m_gpsTimer!=null)
		{
			m_gpsTimer.cancel();
		}
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
	private final ReentrantLock m_lock = new ReentrantLock();
	void lockLocationChanged( Location newLocation )
    {
		m_lock.lock();
		try {
		    if( m_processor.onLocationChanged(newLocation) )
		    {
				onLocationChanged( newLocation );
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
}

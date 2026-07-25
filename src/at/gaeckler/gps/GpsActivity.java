package at.gaeckler.gps;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
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
import android.os.Environment;

public abstract class GpsActivity extends Activity {

	protected static final String	NAME_KEY = "name";

	public static final int AUTO_GPS = 0;
	public static final int FAST_GPS = 100;
	public static final int NORMAL_GPS = 1000;
	public static final int SLOW_GPS = 10000;

	private static final double MAX_SPEED = 100;
	private static final double MAX_ACCEL = 100;
	
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
				lockLocationChanged( location, true );
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
		    	
		    	/// TODO remove 
		    	private Location m_lastKnown=null;
		
		    	@Override
		    	public void onTick(long millisUntilFinished) {
		    		Location newLocation = m_locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		    		if (newLocation != null && (m_lastKnown==null || !m_lastKnown.equals(newLocation)))
		    		{
		    			lockLocationChanged(newLocation, true);
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

    private File				m_file = null;
	private FileOutputStream	m_fileos = null;
	private PrintWriter			m_pos = null; 

	private static final String TRACK_FILE = "temp.gak.gps.txt";

	private static File getExternalFileName( String filename )
    {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);

        System.out.println(dir.getPath());
        if( !dir.exists() )
        {
        	dir.mkdir();
        }
        File file = new File(dir, filename);
        System.out.println(file.getPath());
        
        return file;
    }
	private void openGPSfileOS() throws IOException
	{
		m_file = getExternalFileName(TRACK_FILE);
		m_file.createNewFile();

		m_fileos = new FileOutputStream(m_file, true);
		m_pos = new PrintWriter(m_fileos); 
	}
	private void closeGPSfileOS() throws IOException
	{
		if( m_pos != null )
		{
			m_pos.close();
			m_pos = null;
		}
		if( m_fileos != null )
		{
			m_fileos.close();
			m_fileos = null;
		}
		
	}
	private void appendTrackPoint(Location loc)
	{
        try
		{
        	if( m_pos == null )
        	{
        		openGPSfileOS();
        	}
			m_pos.println(locationString(loc, true));
			m_pos.flush();
			m_fileos.flush();
		}
		catch( Exception e)
		{
			// ignore
		}
    }

	public static boolean between( double min, double cur, double max )
	{
		return ( min <= cur && cur <= max );
	}
	public void readTrackPoints()
	{
		try 
		{
			if( m_file == null )
			{
				m_file = getExternalFileName(TRACK_FILE);
			}
			if( m_file != null )
			{
				BufferedReader  reader = new BufferedReader(new FileReader(m_file));
			
				while( true ) 
				{
					String line = reader.readLine();
					if( line == null )
					{
						break;
					}
					Location newLocation = locationString(line,true);

					// 14.4426064, 48.3637592, 
					// 14.4481877, 48.3570682
/*					
					double lon = newLocation.getLongitude(); 
					double lat = newLocation.getLatitude();

					if( between( 14.44, lon, 14.46 )
					&&  between( 48.350, lat, 48.37 ) )
					{
						System.out.println("I'm in");
					}
*/
					lockLocationChanged(newLocation,false);
				}

				reader.close();
			}
		} 
		catch (IOException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	static private double getEllapsedTime(Location loc1, Location loc2)
	{
		return (double)(loc2.getTime()-loc1.getTime())/1000.0;
	}
	static private double getSpeed(Location loc1, Location loc2)
	{
		return (double)loc1.distanceTo(loc2) / getEllapsedTime(loc1, loc2);
	}
	static private double getAccel(Location loc1, Location loc2)
	{
		return (double)(loc2.getSpeed()-loc1.getSpeed()) / getEllapsedTime(loc1, loc2);
	}
	private final ReentrantLock m_lock = new ReentrantLock();
	private Location[] m_lastLocations;
	private boolean m_goodGps = false;
	private long m_startTime = 0;
	private static final String m_provider = "gps";
	
	void lockLocationChanged( Location newLocation, boolean appendTrack )
    {
		if( m_provider==null || newLocation.getProvider().equalsIgnoreCase("GPS") )
		{
			m_lock.lock();
			try {
				if( appendTrack )
				{
					appendTrackPoint(newLocation);
				}
				
				if( m_startTime==0 || m_lastLocations == null || 
						(!m_goodGps && (newLocation.getTime() - m_startTime) > 60000))
				{
					m_startTime = newLocation.getTime();
					m_lastLocations = new Location[2];
					m_goodGps = false;
				}
	
				if( m_lastLocations[0] == null )
				{
					m_lastLocations[0] = newLocation;
				}
				else if( m_lastLocations[1] == null )
				{
					if(m_lastLocations[0].getTime() < newLocation.getTime() )
					{
						if( !newLocation.hasSpeed() )
						{
							double speed = getSpeed(m_lastLocations[0],newLocation);
							if( speed < MAX_SPEED )
							{
								newLocation.setSpeed((float) speed);
							}
							else
							{
								newLocation = null;
							}
						}
						m_lastLocations[1] = newLocation;
					}
				}
				else
				{
					if(m_lastLocations[1].getTime() < newLocation.getTime() )
					{
						double speed = getSpeed(m_lastLocations[1],newLocation);
						newLocation.setSpeed((float) speed);
						double accel = getAccel(m_lastLocations[1], newLocation ); 
	
						if(speed < MAX_SPEED && accel < MAX_ACCEL )
						{
							m_lastLocations[0] = m_lastLocations[1];
							m_lastLocations[1] = newLocation;
							m_goodGps = true;
							if( m_processor.onLocationChanged(newLocation) )
						    {
								onLocationChanged( newLocation );
						    }
						}
					}
				}
			} finally {
				m_lock.unlock();
			}
		}
    }

	protected void simulateLocationFix(Location newLocation)
	{
		lockLocationChanged( newLocation, false );
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

		try 
		{
			closeGPSfileOS();
		} 
		catch (IOException e) 
		{
			// ignore
		}
		
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
	
	public boolean getIgnoreAccuracy()
	{
		return m_processor.getIgnoreAccuracy();
	}
	public void setIgnoreAccuracy(boolean ignoreAcuracy)
	{
		m_processor.setIgnoreAccuracy( ignoreAcuracy );
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
	public String getAccelStr()
	{
		return m_processor.getAccelStr();
	}
	public double getResolution()
	{
		return m_processor.getResolution();
	}
	public long getBreakTime()
	{
		return m_processor.getBreakTime();
	}
	public void setBreakTime( long breakTime )
	{
		m_processor.setBreakTime(breakTime);
	}
	private static String locationString( Location src, boolean raw )
	{
		String result = src.getProvider() + '|' + 
				Double.toString(src.getLongitude()) + '|' + 
				Double.toString(src.getLatitude()) + '|' +
				Double.toString(src.getAltitude());
		
		if( raw )
		{
			result += '|' + Double.toString(src.getAccuracy()) +
					  '|' + Long.toString(src.getTime());
		}
		return result;
	}
	public static String locationString( Location src )
	{
		return locationString(src, false);
	}
	
	private static Location locationString( String src, boolean raw )
	{
		if(src == null)
		{
/*@*/		return null;
		}
		String [] elements = src.split("[|]");
		if(elements.length < 3)
		{
/*@*/		return null;
		}
		String provider = elements[0];
		double longitude = Double.parseDouble(elements[1]);
		double latitude = Double.parseDouble(elements[2]);
		
		if( Math.abs(longitude) < 0.01 && Math.abs(latitude) < 0.01)
		{
/*@*/		return null;
		}
		Location newLocation = new Location(provider);
		newLocation.setLongitude(longitude);
		newLocation.setLatitude(latitude);
		if (elements.length >= 4)
		{
			newLocation.setAltitude(Double.parseDouble(elements[3]));;
		}

		if( raw )
		{
			if (elements.length < 6)
			{
/*@*/			return null;
			}
			newLocation.setAccuracy((float) Double.parseDouble(elements[4]));
			newLocation.setTime( Long.parseLong(elements[5]) );
		}
		else if (elements.length >= 5)
		{
			String name = elements[4];
			Bundle bundle = new Bundle();
			bundle.putString(NAME_KEY, name);
			newLocation.setExtras(bundle);
		}
		return newLocation;  
	}
	public static Location locationString( String src )
	{
		return locationString( src, false );
	}

}

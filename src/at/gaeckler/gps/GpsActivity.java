/*
		Project:		GPS
		Module:			GpsActivity.java
		Description:	The android activity base for all GPS apps
		Author:			Martin Gäckler
		Address:		Hofmannsthalweg 14, A-4030 Linz
		Web:			https://www.gaeckler.at/

		Copyright:		(c) 2013-2026 Martin Gäckler

		This program is free software: you can redistribute it and/or modify
		it under the terms of the GNU General Public License as published by
		the Free Software Foundation, version 3.

		You should have received a copy of the GNU General Public License
		along with this program. If not, see <http://www.gnu.org/licenses/>.

		THIS SOFTWARE IS PROVIDED BY Martin Gäckler, Linz, Austria ``AS IS''
		AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
		TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
		PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR
		CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
		SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
		LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
		USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
		ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
		OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
		OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
		SUCH DAMAGE.
*/
package at.gaeckler.gps;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.locks.ReentrantLock;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.location.GnssStatus;

import androidx.annotation.DrawableRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public abstract class GpsActivity extends AppCompatActivity
{
	protected static final String	NAME_KEY = "name";

	protected static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
	public static final int AUTO_GPS = 0;
	public static final int FAST_GPS = 100;
	public static final int NORMAL_GPS = 1000;
	public static final int SLOW_GPS = 10000;

	private static final double MAX_SPEED = 100;
	private static final double MAX_ACCEL = 100;

	public static final int GPS_EVENT_STARTED = 1;
	public static final int GPS_EVENT_SATELLITE_STATUS = 2;
	public static final int GPS_EVENT_FIRST_FIX = 3;
	public static final int GPS_EVENT_STOPPED = 4;

	CountDownTimer		m_gpsTimer = null;
	LocationManager		m_locationManager = null;
	private LocationListener	m_locationListener = null;

	private GnssStatus.Callback	m_gnssStatusListener = null;
	private final GpsProcessor	m_processor = new GpsProcessor();
	private int m_gpsInterval = 0;

	private static final String	CALIBRATION_KEY = "calibrationMode";
	private static final String	FIX_COUNT_KEY = "fixCount";
	private static final String	SUM_LONGITUDE_KEY = "sumLongitude";
	private static final String	SUM_LATITUDE_KEY = "sumLatitude";
	private static final String	SUM_ALTITUDE_KEY = "sumAltitude";

	private boolean	m_calibration = false;
	private double	m_sumLongitude = 0;
	private double	m_sumLatitude = 0;
	private double	m_sumAltitude = 0;
	private long	m_locationFixCount = 0;

	public interface DialogCallback {
		void onConfirmed(boolean confirmed);
	}

	public void showMessage(@DrawableRes int iconId, String title, String message, final boolean terminate, DialogCallback callback )
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(message)
				.setTitle(title)
				.setCancelable(false)
				.setPositiveButton("OK", (dialog, id) ->
				{
					dialog.dismiss();
					if (terminate) {
						finish();
					}
					if (callback != null)
						callback.onConfirmed(true);
				})
				.setIcon(iconId)
		;
		if( callback != null )
		{
			builder.setNegativeButton("Abbruch", (dialog, id) ->
			{
				dialog.cancel();
				if (callback != null) callback.onConfirmed(false);
			});
		}
		AlertDialog alert = builder.create();
		alert.show();
	}


	public boolean isCalibrationMode()
	{
		return m_calibration;
	}
	public void enableCalibartion()
	{
		if( !m_calibration )
		{
			m_calibration = true;
			m_sumLongitude = 0;
			m_sumLatitude = 0;
			m_sumAltitude = 0;
			m_locationFixCount = 0;
		}
	}
	public void disableCalibartion()
	{
		m_calibration = false;
	}
	public Location getCalibratedLocation( String provider )
	{
		Location location = new Location(provider);
		double longitude = m_sumLongitude/m_locationFixCount;
		double latitude = m_sumLatitude/m_locationFixCount;
		double altitude = m_sumAltitude/m_locationFixCount;
		location.setLongitude(longitude);
		location.setLatitude(latitude);
		location.setAltitude(altitude);

		return location;
	}

	public long getLocationFixCount()
	{
		return m_locationFixCount;
	}
	
	public abstract void onLocationEnabled();
	public abstract void onLocationDisabled();
	public abstract void onGnssStatusChanged2(int event, GnssStatus status);
	public abstract void onLocationChanged( Location newLocation );

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
				!= PackageManager.PERMISSION_GRANTED)
		{
			// Suggestion: Request the permission instead of just failing
			ActivityCompat.requestPermissions(this,
				new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
				LOCATION_PERMISSION_REQUEST_CODE
			);
			finish();
			return;
		}
		if( savedInstanceState != null ) {
			m_calibration = savedInstanceState.getBoolean(CALIBRATION_KEY, false);
			m_locationFixCount = savedInstanceState.getLong(FIX_COUNT_KEY, 0);
			m_sumLongitude = savedInstanceState.getDouble(SUM_LONGITUDE_KEY, 0);
			m_sumLatitude = savedInstanceState.getDouble(SUM_LATITUDE_KEY, 0);
			m_sumAltitude = savedInstanceState.getDouble(SUM_ALTITUDE_KEY, 0);
		}

		// Acquire a reference to the system Location Manager
		m_locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

		// Define a listener that responds to location updates
		m_locationListener = new LocationListener()
		{
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

		m_gnssStatusListener = new GnssStatus.Callback()
		{
			@Override
			public void onStarted()
			{
				super.onStarted();
				onGnssStatusChanged2(GPS_EVENT_STARTED, null);
			}

			@Override
			public void onFirstFix(int ttffMillis)
			{
				super.onFirstFix(ttffMillis);
				onGnssStatusChanged2(GPS_EVENT_FIRST_FIX, null);
			}
			@Override
			public void onStopped()
			{
				super.onStopped();
				onGnssStatusChanged2(GPS_EVENT_STOPPED, null);
			}
			public void onSatelliteStatusChanged(GnssStatus status)
			{
				super.onSatelliteStatusChanged(status);
				onGnssStatusChanged2(GPS_EVENT_SATELLITE_STATUS, status);
			}
		};
		System.out.println("addGnssStatusListener");
		m_locationManager.registerGnssStatusCallback(m_gnssStatusListener, null);

		// Register the listener with the Location Manager to receive location updates
		m_locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 50, (float) 0.1, m_locationListener);
		m_locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 50, (float) 0.1, m_locationListener);

		createGpsTimer(NORMAL_GPS);
	}
	
	@Override
	protected void  onSaveInstanceState (Bundle outState)
	{
		super.onSaveInstanceState(outState);

		outState.putLong(FIX_COUNT_KEY, m_locationFixCount);
		outState.putBoolean(CALIBRATION_KEY, m_calibration);
		outState.putDouble(SUM_LONGITUDE_KEY, m_sumLongitude);
		outState.putDouble(SUM_LATITUDE_KEY, m_sumLatitude);
		outState.putDouble(SUM_ALTITUDE_KEY, m_sumAltitude);
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
				@SuppressLint("MissingPermission")
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

	private Boolean				m_logTrack = false;
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
		if(!m_logTrack || !Environment.isExternalStorageManager())
		{
			return;
		}
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
		if(!Environment.isExternalStorageManager())
		{
			return;
		}

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
				m_logTrack = true;
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
	
	void lockLocationChanged( Location newLocation, boolean fromGPS )
	{
		if( m_provider==null || newLocation.getProvider().equalsIgnoreCase("GPS") )
		{
			m_lock.lock();
			try {
				if( fromGPS )
				{
					++m_locationFixCount;
					if( m_calibration )
					{
						m_sumLongitude += newLocation.getLongitude();
						m_sumLatitude += newLocation.getLatitude();
						m_sumAltitude += newLocation.getAltitude();
					}
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

	@Override
	public void onDestroy()
	{
		// Acquire a reference to the system Location Manager
		// LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

		m_locationManager.removeUpdates( m_locationListener );
//		m_locationManager.removeGpsStatusListener( m_gpsStatusListener );
		m_locationManager.unregisterGnssStatusCallback(m_gnssStatusListener);
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
				src.getLongitude() + '|' +
				src.getLatitude() + '|' +
				src.getAltitude();
		
		if( raw )
		{
			result += '|' + src.getAccuracy() +
					  '|' + src.getTime();
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
			newLocation.setAltitude(Double.parseDouble(elements[3]));
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

	// may be this is useful
	//public boolean isDarkModeActive(Context context)
	//{
	//	int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
	//
	//	return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
	//}
}

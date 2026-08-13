/*
		Project:		GPS
		Module:			GpsService.java
		Description:	The service that allows receiving GPS events without a running activity
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

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

public class GpsService extends Service implements LocationListener
{
	private LocationManager		m_locationManager = null;

	private GpsLogger m_gpsLogger = null;
	private GpsReceiver m_gpsReceiver = null;
	private final GpsProcessor	m_processor = new GpsProcessor();


	public static final int AUTO_GPS = 0;				// let the GPS system decide when to send new positions
	public static final int FAST_GPS = 100;				// ask every 100ms for a new position
	public static final int NORMAL_GPS = 1000;			// ask every Second for a new position
	public static final int SLOW_GPS = 10000;			// ask every 10 seconds for a new position

	private CountDownTimer		m_gpsTimer = null;
	private int					m_gpsInterval = 0;
	private boolean				m_gpsEnabled = false;

	/**
	 * Get the GPS status
	 * @return true if GPS is enabled, false otherwise
	 */
	public boolean isGpsEnabled()
	{
		return m_gpsEnabled;
	}

	/**
	 * Create the GPS timer that periodically checks the location
	 * @param interval the interval in milliseconds
	 */
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

				@Override
				@SuppressLint("MissingPermission")
				public void onTick(long millisUntilFinished) {
					Location newLocation = m_locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
					if( newLocation != null )
					{
						onLocationChanged(newLocation);
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

	/**
	 * Remove the GPS timer
	 */
	public void removeGpsTimer()
	{
		if (m_gpsTimer!=null)
		{
			m_gpsTimer.cancel();
			m_gpsTimer = null;
			m_gpsInterval = 0;
		}
	}

	/**
	 * Get the GPS interval
	 * @return the interval in milliseconds
	 */
	public int getInterval()
	{
		return m_gpsInterval;
	}

	/*
		--------------------------------------------------------------------------------------------
			Calibration
		--------------------------------------------------------------------------------------------
	*/
	private boolean	m_calibration = false;
	private int		m_prevInterval = 0;
	private double	m_sumLongitude = 0;
	private double	m_sumLatitude = 0;
	private double	m_sumAltitude = 0;
	private long	m_locationCalibrationCount = 0;

	/**
	 * Enable calibration if not yet enabled
	 */
	public void enableCalibration()
	{
		if( !m_calibration )
		{
			m_prevInterval = m_gpsInterval;
			m_calibration = true;
			m_sumLongitude = 0;
			m_sumLatitude = 0;
			m_sumAltitude = 0;
			m_locationCalibrationCount = 0;
		}
	}

	/**
	 * Disable calibration
	 */
	public void disableCalibration()
	{
		if( m_calibration )
			createGpsTimer(m_prevInterval);
		m_calibration = false;
	}

	/**
	 * Get the calibrated location
	 * @param provider to be used for the location
	 * @return the calibrated location (the mean of all gps location since calibration started)
	 */
	public Location getCalibratedLocation( String provider )
	{
		Location location = new Location(provider);
		double longitude = m_sumLongitude/m_locationCalibrationCount;
		double latitude = m_sumLatitude/m_locationCalibrationCount;
		double altitude = m_sumAltitude/m_locationCalibrationCount;
		location.setLongitude(longitude);
		location.setLatitude(latitude);
		location.setAltitude(altitude);

		return location;
	}

	/**
	 * Check if calibration is enabled
	 * @return true if calibration is enabled, false otherwise
	 */
	public boolean getCalibration()
	{
		return m_calibration;
	}

	/**
	 * Get the number of location fixes
	 * @return the number of location fixes
	 */
	public long getLocationFixCount()
	{
		if( m_calibration )
			return m_locationCalibrationCount;
		else if( m_gpsLogger.getTrackGps() )
			return m_gpsLogger.getLocationFixCount();
		else
			return m_gpsReceiver.getLocationFixCount();
	}

	/*
		--------------------------------------------------------------------------------------------
			Notification
		--------------------------------------------------------------------------------------------
	*/

	private static final String CHANNEL_STR_ID = "GpsServiceChannel";
	private static final String CHANNEL_NAME = "GPS Service Channel";
	static final String FROM_NOTIFICATION = "FROM_NOTIFICATION";
	private static final int CHANNEL_ID_NUMBER = 1;

	private Notification createNotification( String newTitle, String newText, Class<?> target )
	{
		Intent intent = new Intent(this, target);
		intent.putExtra(FROM_NOTIFICATION, true);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

		PendingIntent pendingIntent = PendingIntent.getActivity(
				this,
				0,
				intent,
				PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
		);

		return new NotificationCompat.Builder(this, CHANNEL_STR_ID)
			.setContentTitle(newTitle)
			.setContentText(newText)
			.setSmallIcon(android.R.drawable.ic_menu_mylocation)
			.setContentIntent(pendingIntent)
			.setOngoing(true)
			.build()
		;
	}

	private Notification createNotificationChannel()
	{
		NotificationChannel serviceChannel = new NotificationChannel(
			CHANNEL_STR_ID,
			CHANNEL_NAME,
			NotificationManager.IMPORTANCE_LOW
		);
		NotificationManager manager = getSystemService(NotificationManager.class);
		if (manager != null)
		{
			manager.createNotificationChannel(serviceChannel);
		}
		// here we can not yet user translations => we use english literals and the activity
		// will handle the translation
		return createNotification("GPS Tracking active", "GPS Tracking active", GpsActivity.class );
	}

	/**
	 * Update the notification
	 * @param newTitle the new title
	 * @param newText the new text
	 */
	public void updateNotification(String newTitle, String newText, Class<?> target)
	{
		NotificationManager manager = getSystemService(NotificationManager.class);
		manager.notify(CHANNEL_ID_NUMBER, createNotification(newTitle, newText, target));
	}

	/*
		--------------------------------------------------------------------------------------------
			The Service Interface
		--------------------------------------------------------------------------------------------
	 */
	@SuppressLint("MissingPermission")
	@Override
	public void onCreate()
	{
		super.onCreate();

		// Acquire a reference to the system Location Manager
		m_locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

		m_gpsLogger = new GpsLogger(this, getSharedPreferences(GpsActivity.CONFIG_FILE, MODE_PRIVATE).getString(GpsActivity.CONFIG_KEY, null) );
		m_gpsReceiver = new GpsReceiver(m_processor, m_gpsLogger);
	}

	private void checkInitialGpsStatus()
	{
		LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		if (locationManager != null) {
			boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
			if (isGpsEnabled)
			{
				onProviderEnabled(LocationManager.GPS_PROVIDER);
			}
			else
			{
				onProviderDisabled(LocationManager.GPS_PROVIDER);
			}
		}
	}
	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		startForeground(CHANNEL_ID_NUMBER, createNotificationChannel());

		try
		{
			m_locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 50, (float) 0.1, this);
			m_locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 50, (float) 0.1, this);
			createGpsTimer(AUTO_GPS);
			checkInitialGpsStatus();
		}
		catch (SecurityException e)
		{
			Log.e(getPackageName(), "Permission denied", e);
		}

		return START_STICKY;
	}

	@Override
	public void onDestroy()
	{
		m_gpsLogger.onStop();
		m_gpsLogger.onDestroy();

		if( m_locationManager != null )
		{
			m_locationManager.removeUpdates(this);
		}

		super.onDestroy();
	}

	private final IBinder m_binder = new LocalBinder();
	public class LocalBinder extends Binder
	{
		public GpsService getService() {
			return GpsService.this;
		}
	}
	@Override
	public IBinder onBind(Intent intent)
	{
		return m_binder;
	}

	/*
		--------------------------------------------------------------------------------------------
			The Location Listener Interface
		--------------------------------------------------------------------------------------------
	 */
	@Override
	public void onLocationChanged(@NonNull Location newLocation)
	{
		if( m_calibration )
		{
			m_sumLongitude += newLocation.getLongitude();
			m_sumLatitude += newLocation.getLatitude();
			m_sumAltitude += newLocation.getAltitude();
			m_locationCalibrationCount++;
		}
		m_gpsReceiver.lockLocationChanged(newLocation, true, this::broadcastLocation);
	}

	@Override
	public void onProviderEnabled(@NonNull String provider)
	{
		if( LocationManager.GPS_PROVIDER.equals(provider) )
		{
			m_gpsEnabled = true;
			broadcastGpsEnabled(provider);
		}
	}

	@Override
	public void onProviderDisabled(@NonNull String provider)
	{
		if( LocationManager.GPS_PROVIDER.equals(provider) )
		{
			m_gpsEnabled = false;
			broadcastGpsDisabled(provider);
		}
	}

	/*
		--------------------------------------------------------------------------------------------
			broadcast
		--------------------------------------------------------------------------------------------
	*/

	// send a broad cast message
	public static final String ACTION_GPS_DATA = "at.gaeckler.gps.NEW_LOCATION";
	public static final String EXTRA_LOCATION = "EXTRA_LOCATION";
	public static final String EXTRA_GPS_ENABLED = "EXTRA_GPS_ENABLED";
	public static final String EXTRA_GPS_DISABLED = "EXTRA_GPS_DISABLED";

	public void broadcastLocation(Location location)
	{
		Intent intent = new Intent(ACTION_GPS_DATA);

		intent.setPackage(getPackageName());

		// Die Location ist "Parcelable", kann also direkt ins Paket gesteckt werden
		intent.putExtra(EXTRA_LOCATION, location);

		// Die Nachricht ins System werfen
		sendBroadcast(intent);
	}

	public void broadcastGpsEnabled( String provider )
	{
		Intent intent = new Intent(ACTION_GPS_DATA);

		intent.setPackage(getPackageName());

		intent.putExtra(EXTRA_GPS_ENABLED, provider);

		// Die Nachricht ins System werfen
		sendBroadcast(intent);
	}

	public void broadcastGpsDisabled( String provider )
	{
		Intent intent = new Intent(ACTION_GPS_DATA);

		intent.setPackage(getPackageName());

		intent.putExtra(EXTRA_GPS_DISABLED, provider);

		// Die Nachricht ins System werfen
		sendBroadcast(intent);
	}

	/*
		--------------------------------------------------------------------------------------------
		receiver and logger
		--------------------------------------------------------------------------------------------
	*/
	public GpsReceiver getGpsReceiver()
	{
		return m_gpsReceiver;
	}

	public GpsLogger getGpsLogger()
	{
		return m_gpsLogger;
	}

	/*
		--------------------------------------------------------------------------------------------
			Interface to the GpsProcessor
		--------------------------------------------------------------------------------------------
	 */

	/**
	 * Check if a location is available
	 * @return true if a location is available, false otherwise
	 */
	public boolean hasLocation()
	{
		return m_processor.hasLocation();
	}

	/**
	 * Get the last location
	 * @return the last location null if no location is available
	 */
	public Location lastLocation()
	{
		return m_processor.lastLocation();
	}

	/**
	 * Check if the accuracy of a GPS fix is ignored
	 * @return the value
	 */
	public boolean getIgnoreAccuracy()
	{
		return m_processor.getIgnoreAccuracy();
	}

	/**
	 * Set if the accuracy of a GPS fix should be ignored
	 * @param ignoreAcuracy the value	 */
	public void setIgnoreAccuracy(boolean ignoreAcuracy)
	{
		m_processor.setIgnoreAccuracy( ignoreAcuracy );
	}

	/**
	 * Get the accuracy
	 * @return the accuracy of the last gps fix
	 */
	public double getAccuracy()
	{
		return m_processor.getAccuracy();
	}

	/**
	 * Get the number of locations in the current buffer
	 * @return the num of locations
	 */
	public int getNumLocations()
	{
		return m_processor.getNumLocations();
	}

	/**
	 * Get the current bearing
	 * @return the current bearing
	 */
	public double getCurBearing()
	{
		return m_processor.getCurBearing();
	}

	/**
	 * Get the current speed
	 * @return the current speed in m/s
	 */
	public double getSpeed()
	{
		return m_processor.getSpeed();
	}

	/**
	 * Get the current acceleration
	 * @return the current acceleration in m/s^2
	 */
	public double getAccel()
	{
		return m_processor.getAccel();
	}

	/**
	 * Get the current acceleration as a string for the UI
	 * @return the current acceleration as a string
	 */
	public String getAccelStr()
	{
		return m_processor.getAccelStr();
	}

	/**
	 * Get the resolution if the GPS receiver
	 * @return the resolution
	 */
	public double getResolution()
	{
		return m_processor.getResolution();
	}

	/**
	 * Get the brake time (the time reducing the current speed=
	 * @return the time in milliseconds
	 */
	public long getBrakeTime()
	{
		return m_processor.getBrakeTime();
	}

	/**
	 * Resets the measuring of the brake time
	 * Set the beake time (the time reducing the current speed=
	 * @param brakeTime the time in milliseconds
	 */
	public void setBrakeTime( long brakeTime )
	{
		m_processor.setBrakeTime(brakeTime);
	}
}
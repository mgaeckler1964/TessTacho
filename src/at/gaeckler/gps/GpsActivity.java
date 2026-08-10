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

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.location.GnssStatus;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import at.gaeckler.MyActivity;

public abstract class GpsActivity extends MyActivity
{
	// GPS events, handle these events in your onGnssStatusChanged2
	public static final int GPS_EVENT_STARTED = 1;				// GPS started GnssStatus.Callback received onStarted()
	public static final int GPS_EVENT_SATELLITE_STATUS = 2;		// GPS started GnssStatus.Callback received onSatelliteStatusChanged()
	public static final int GPS_EVENT_FIRST_FIX = 3;			// GPS started GnssStatus.Callback received onFirstFix()
	public static final int GPS_EVENT_STOPPED = 4;				// GPS started GnssStatus.Callback received onStopped()

	/*
	-----------------------------------------------------------------------------------------------
		Helper
	-----------------------------------------------------------------------------------------------
	 */
	/**
	 * Check if the given value is between the given min and max
	 * @param min the minimum value
	 * @param cur the current value
	 * @param max the maximum value
	 * @return true if the value is between the given min and max, false otherwise
	 */
	public static boolean between( double min, double cur, double max )
	{
		return ( min <= cur && cur <= max );
	}

	/*
	-----------------------------------------------------------------------------------------------
		Calibration
	-----------------------------------------------------------------------------------------------
	 */
	private static final String	CALIBRATION_KEY = "calibrationMode";
	private static final String	FIX_COUNT_KEY = "fixCount";
	private static final String	SUM_LONGITUDE_KEY = "sumLongitude";
	private static final String	SUM_LATITUDE_KEY = "sumLatitude";
	private static final String	SUM_ALTITUDE_KEY = "sumAltitude";

	private boolean	m_calibration = false;
	private double	m_sumLongitude = 0;
	private double	m_sumLatitude = 0;
	private double	m_sumAltitude = 0;
	private long	m_locationCalibrationCount = 0;

	/**
	 * Enable calibration if not yet enabled
	 */
	public  void enableCalibration()
	{
		if( !m_calibration )
		{
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
	protected void disableCalibration()
	{
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
	protected boolean getCalibration()
	{
		return m_calibration;
	}

	/**
	 * Get the number of location fixes
	 * @return the number of location fixes
	 */
	public long getLocationFixCount()
	{
		return m_calibration ? m_locationCalibrationCount : m_gpsReceiver.getLocationFixCount();
	}


	/*
	-----------------------------------------------------------------------------------------------
		Permissions
	-----------------------------------------------------------------------------------------------
	 */
	private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
	private static final int STORAGE_PERMISSION_REQUEST_CODE = 1002;

	/**
	 * Check if the location permission is granted
	 * @return true if the location permission is granted, false otherwise
	 */
	public boolean checkLocationPermission()
	{
		return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
	}

	/**
	 * Request the location permission
	 * @return false if the permission is already granted, true otherwise
	 */
	public boolean requestLocationPermission()
	{
		if(checkLocationPermission())    // already granted
		{
			return false;
		}

		// Suggestion: Request the permission instead of just failing
		ActivityCompat.requestPermissions(this,
				new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
				LOCATION_PERMISSION_REQUEST_CODE
		);
		return true;
	}

	/**
	 * Prüft, ob für die gespeicherte SAF-Uri noch Rechte vorliegen.
	 */
	private  boolean checkSafFolderPermissions( boolean writePermission )
	{
		String uriString = getSharedPreferences(CONFIG_FILE, MODE_PRIVATE).getString(CONFIG_KEY, null);
		if (uriString == null)
			return false;
		Uri treeUri = Uri.parse(uriString);
		if (treeUri == null)
			return false;

		int modeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
		if( writePermission )
			modeFlags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

		try
		{
			// Diese Methode wirft keine Exception, wenn man keine Rechte hat,
			// aber man kann damit prüfen, ob der Zugriff noch valide ist.
			getContentResolver().takePersistableUriPermission(treeUri, modeFlags);
		}
		catch (SecurityException e)
		{
			return false;
		}

		// 2. Prüfung via DocumentFile (ist der Ordner noch vorhanden?)
		DocumentFile folder = DocumentFile.fromTreeUri(this, treeUri);
		return folder != null && folder.exists()
				&& folder.canRead()
				&& (!writePermission || folder.canWrite());
	}

	/**
	 * Check if the device is running on a device with an external storage manager
	 * @return true if the device is running on a device with an external storage manager, false otherwise
	 */
	public boolean checkIsExternalStorageManager()
	{
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager();
	}

	/**
	 * Check if the storage read permission is granted
	 * @return true if the storage read permission is granted, false otherwise
	 */
	public boolean checkReadStoragePermission()
	{
		return checkIsExternalStorageManager()
			|| checkSafFolderPermissions(false)
			|| ContextCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
		;
	}

	/**
	 * Check if the storage write permission is granted
	 * @return true if the storage write permission is granted, false otherwise
	 */
	public boolean checkWriteStoragePermission()
	{
		if( checkIsExternalStorageManager() )
			return true;
		return checkSafFolderPermissions(true) || ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
	}

	public void displayStorageManagePermission()
	{
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
		{
			try
			{
				Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
				intent.addCategory("android.intent.category.DEFAULT");
				intent.setData(Uri.parse(String.format("package:%s", getPackageName())));
				startActivity(intent);
			} catch(Exception e)
			{
				Intent intent = new Intent();
				intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
				startActivity(intent);
			}
		}
	}

	/**
	 * The request code for the storage permission
	 * rcOK: permission already granted
	 * rcDenied: permission denied (not requested)
	 * rcRequested: permission denied (requested)
	 */
	public enum RequestCode { OK, DENIED, REQUESTED }

	/**
	 * Request the storage permission
	 * @param iconId the icon to use
	 * @param title the title to use
	 * @return the request code
	 */
	public RequestCode requestStoragePermission( @DrawableRes int iconId, String title )
	{
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
		{
			if( hasStorageFolder() )
				return RequestCode.OK;

			if( !Environment.isExternalStorageManager() )
			{
				displayStorageManagePermission();
				return RequestCode.REQUESTED;
			}
		}
		else if(
				ContextCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE)
						!= PackageManager.PERMISSION_GRANTED
						|| ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE)
						!= PackageManager.PERMISSION_GRANTED
		)
		{
			// Suggestion: Request the permission instead of just failing
			ActivityCompat.requestPermissions(
					this,
					new String[]{READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE},
					STORAGE_PERMISSION_REQUEST_CODE
			);
			return RequestCode.DENIED;
		}
		else if( checkCallingOrSelfPermission("android.permission.READ_EXTERNAL_STORAGE") == PackageManager.PERMISSION_DENIED )
		{
			showMessage(iconId, title, "Read Permission Missing", true, null);
			return RequestCode.DENIED;
		}
		else if( checkCallingOrSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") == PackageManager.PERMISSION_DENIED )
		{
			showMessage( iconId, title, "Write Premission Missing", true, null);
			return RequestCode.DENIED;
		}
		return RequestCode.OK;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
	{
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if (requestCode == LOCATION_PERMISSION_REQUEST_CODE || requestCode == STORAGE_PERMISSION_REQUEST_CODE)
		{
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
			{
				// User said YES!
				// Now you can call the initialization logic you skipped in onCreate
				recreate(); // The easiest way: restart the activity now that we have permission
			}
			else
			{
				// User said NO.
				// NOW you can call finish() because the app truly cannot work.
				if(requestCode == LOCATION_PERMISSION_REQUEST_CODE)
				{
					showMessage(0, getLocalClassName(), "Fine Location Permission Missing!", true, null);
				}
				else
				{
					finish();
				}
			}
		}
	}

	/*
	-----------------------------------------------------------------------------------------------
		Basic Activity implementation
	-----------------------------------------------------------------------------------------------
	 */

	private LocationManager		m_locationManager = null;
	private LocationListener	m_locationListener = null;
	private GnssStatus.Callback	m_gnssStatusListener = null;
	public static final int AUTO_GPS = 0;				// let the GPS system decide when to send new positions
	public static final int FAST_GPS = 100;				// ask every 100ms for a new position
	public static final int NORMAL_GPS = 1000;			// ask every Second for a new position
	public static final int SLOW_GPS = 10000;			// ask every 10 seconds for a new position

	/** Called when the activity is first created. */
	@SuppressLint("MissingPermission")
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		if(requestLocationPermission())
		{
			return;
		}

		if( savedInstanceState != null )
		{
			m_calibration = savedInstanceState.getBoolean(CALIBRATION_KEY, false);
			m_locationCalibrationCount = savedInstanceState.getLong(FIX_COUNT_KEY, 0);
			m_sumLongitude = savedInstanceState.getDouble(SUM_LONGITUDE_KEY, 0);
			m_sumLatitude = savedInstanceState.getDouble(SUM_LATITUDE_KEY, 0);
			m_sumAltitude = savedInstanceState.getDouble(SUM_ALTITUDE_KEY, 0);
		}

		m_gpsLogger = new GpsLogger(this, getSharedPreferences(CONFIG_FILE, MODE_PRIVATE).getString(CONFIG_KEY, null) );
		m_gpsReceiver = new GpsReceiver(m_processor, m_gpsLogger);

		// Acquire a reference to the system Location Manager
		m_locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

		// Define a listener that responds to location updates
		m_locationListener = new LocationListener()
		{
			@Override
			public void onProviderEnabled(@NonNull String provider)
			{
				onLocationEnabled();
			}

			@Override
			public void onProviderDisabled(@NonNull String provider)
			{
				onLocationDisabled();
			}

			@Override
			public void onLocationChanged(@NonNull Location location)
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
			public void onSatelliteStatusChanged(@NonNull GnssStatus status)
			{
				super.onSatelliteStatusChanged(status);
				onGnssStatusChanged2(GPS_EVENT_SATELLITE_STATUS, status);
			}
		};
		m_locationManager.registerGnssStatusCallback(m_gnssStatusListener, null);

		// Register the listener with the Location Manager to receive location updates
		m_locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 50, (float) 0.1, m_locationListener);
		m_locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 50, (float) 0.1, m_locationListener);

		createGpsTimer(NORMAL_GPS);
	}

	@Override
	protected void  onSaveInstanceState( @NonNull Bundle outState )
	{
		super.onSaveInstanceState(outState);

		outState.putLong(FIX_COUNT_KEY, m_locationCalibrationCount);
		outState.putBoolean(CALIBRATION_KEY, m_calibration);
		outState.putDouble(SUM_LONGITUDE_KEY, m_sumLongitude);
		outState.putDouble(SUM_LATITUDE_KEY, m_sumLatitude);
		outState.putDouble(SUM_ALTITUDE_KEY, m_sumAltitude);
	}

	@Override
	protected void onStop()
	{
		m_gpsLogger.onStop();
		super.onStop();
	}

	@Override
	protected void onDestroy()
	{
		if( m_locationManager != null )
		{
			m_locationManager.removeUpdates(m_locationListener);
			m_locationManager.unregisterGnssStatusCallback(m_gnssStatusListener);
		}
		m_gpsLogger.onDestroy();
		super.onDestroy();
	}

	/*
	-----------------------------------------------------------------------------------------------
		Storage Access Framework (SAF)
	-----------------------------------------------------------------------------------------------
	 */
	private static final int REQUEST_CODE_OPEN_DIRECTORY = 1234;
	private static final String CONFIG_FILE = "prefs";
	private static final String CONFIG_KEY = "storage_folder_uri";

	/**
	 * Select the storage folder
	 */
	@SuppressWarnings("deprecation")
	public void selectStorageFolder()
	{
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		// Erlaubt dauerhaften Zugriff
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
				| Intent.FLAG_GRANT_WRITE_URI_PERMISSION
				| Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
		startActivityForResult(intent, REQUEST_CODE_OPEN_DIRECTORY);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == REQUEST_CODE_OPEN_DIRECTORY && resultCode == RESULT_OK)
		{
			if (data != null && data.getData() != null)
			{
				Uri treeUri = data.getData();

				// WICHTIG: Die Berechtigung dauerhaft beim System registrieren ("Persistable Permission")
				final int takeFlags = data.getFlags()
						& (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

				try
				{
					getContentResolver().takePersistableUriPermission(treeUri, takeFlags);

					// URI in SharedPreferences speichern
					String uriString = treeUri.toString();
					getSharedPreferences(CONFIG_FILE, MODE_PRIVATE)
							.edit()
							.putString(CONFIG_KEY, uriString)
							.apply();
					m_gpsLogger.setUriString(uriString);

					// Aktivität neu starten oder Initialisierung fortsetzen
					recreate();
				}
				catch (SecurityException e)
				{
					Log.e(getLocalClassName(), "Failed to take persistable permission", e);
				}
			}
		}
	}

	/**
	 * Select the public document folder as storage folder
	 */
	public void selectPublicFolder()
	{
		getSharedPreferences(CONFIG_FILE, MODE_PRIVATE)
				.edit()
				.remove(CONFIG_KEY)
				.apply();
	}

	/**
	 * Check if a storage folder is selected
	 * @return true if a storage folder is selected, false otherwise
	 */
	public boolean hasStorageFolder()
	{
		String uriString = getSharedPreferences(CONFIG_FILE, MODE_PRIVATE).getString(CONFIG_KEY, null);

		return uriString != null;
	}

	/*
	-----------------------------------------------------------------------------------------------
		Basic GPS handling
	-----------------------------------------------------------------------------------------------
	 */

	protected GpsLogger m_gpsLogger = null;
	private GpsReceiver m_gpsReceiver = null;

	private CountDownTimer		m_gpsTimer = null;
	private int					m_gpsInterval = 0;

	public abstract void onLocationEnabled();
	public abstract void onLocationDisabled();
	public abstract void onGnssStatusChanged2(int event, GnssStatus status);
	public abstract void onLocationChanged( Location newLocation );

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
						m_gpsReceiver.lockLocationChanged(newLocation, true, (loc)->onLocationChanged( loc ));
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

	private void lockLocationChanged(@NonNull Location newLocation, boolean fromGPS )
	{
		if( m_calibration && fromGPS )
		{
			m_sumLongitude += newLocation.getLongitude();
			m_sumLatitude += newLocation.getLatitude();
			m_sumAltitude += newLocation.getAltitude();
			m_locationCalibrationCount++;
		}
		m_gpsReceiver.lockLocationChanged(newLocation, fromGPS, this::onLocationChanged);
	}

	/**
	 * Simulate a location fix
	 * @param newLocation the location to simulate
	 */
	protected void simulateLocationFix( @NonNull Location newLocation)
	{
		m_gpsReceiver.lockLocationChanged( newLocation, false, this::onLocationChanged);
	}

	/*
	-----------------------------------------------------------------------------------------------
		Interface to the GpsProcessor
	-----------------------------------------------------------------------------------------------
	 */

	private final GpsProcessor	m_processor = new GpsProcessor();

	/**
	 * Check if a location is available
	 * @return true if a location is available, false otherwise
	 */
	public boolean getHasLocation()
	{
		return m_processor.hasLocation();
	}

	/**
	 * Get the last location
	 * @return the last location null if no location is available
	 */
	public Location getLastLocation()
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

	// may be this is useful
	//public boolean isDarkModeActive(Context context)
	//{
	//	int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
	//
	//	return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
	//}
}

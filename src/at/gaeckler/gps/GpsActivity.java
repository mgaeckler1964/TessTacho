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

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.IntentCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.location.GnssStatus;
import android.os.IBinder;
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

// -------------------------------------------------------------------------------------------------
// region Helper
// -------------------------------------------------------------------------------------------------
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
//endregion

// -------------------------------------------------------------------------------------------------
// region GPS Service
// -------------------------------------------------------------------------------------------------

	private GpsService	m_service = null;
	private boolean		m_serviceBound = false;
	private boolean		m_serviceConfigured = false;

	public void startGpsService()
	{
		Intent serviceIntent = new Intent(this, GpsService.class);
		startForegroundService(serviceIntent);
	}

	public void stopGpsService()
	{
		Intent serviceIntent = new Intent(this, GpsService.class);
		stopService(serviceIntent);
	}

	public boolean isServiceBound()
	{
		return m_serviceBound && m_service != null;
	}

	private final ServiceConnection m_connection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			if( m_serviceBound )
				return;					// nothing to do

			GpsService.LocalBinder binder = (GpsService.LocalBinder) service;
			m_service = binder.getService();
			m_serviceBound = true;
			if( !m_serviceConfigured )
			{
				onConfigureService();
				m_serviceConfigured = true;
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0)
		{
			m_serviceBound = false;
		}
	};

	@Override
	protected void onStart()
	{
		super.onStart();
		if( checkLocationPermission() )
		{
			Intent intent = new Intent(this, GpsService.class);
			bindService(intent, m_connection, Context.BIND_AUTO_CREATE);
		}
	}
	@Override
	protected void onStop()
	{
		if (m_serviceBound)
		{
			unbindService(m_connection);
			m_serviceBound = false;
		}
		super.onStop();
	}

	protected void onConfigureService()
	{
		m_service.getGpsLogger().setUriString(
			getSharedPreferences(CONFIG_FILE, MODE_PRIVATE).getString(CONFIG_KEY, null)
		);
	}

	protected GpsService getService()
	{
		return m_serviceBound ? m_service : null;
	}
//endregion

// -------------------------------------------------------------------------------------------------
// region Broad cast receiver
// -------------------------------------------------------------------------------------------------
	private final BroadcastReceiver m_bcReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			if( GpsService.ACTION_GPS_DATA.equals(intent.getAction()) )
			{
				if( intent.hasExtra(GpsService.EXTRA_LOCATION) )
				{
					Location loc = IntentCompat.getParcelableExtra(intent, GpsService.EXTRA_LOCATION, Location.class);
					if(loc != null)
					{
						onLocationChanged(loc);
					}
				}
				else if( intent.hasExtra(GpsService.EXTRA_GPS_ENABLED) )
				{
					m_gpsEnabled = true;
					onLocationEnabled();
				}
				else if( intent.hasExtra(GpsService.EXTRA_GPS_DISABLED) )
				{
					m_gpsEnabled = false;
					onLocationDisabled();
				}
			}
		}
	};

	// register the receiver
	@Override
	protected void onResume()
	{
		super.onResume();
		IntentFilter filter = new IntentFilter(GpsService.ACTION_GPS_DATA);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
		{
			registerReceiver(m_bcReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
		}
		else
		{
			// Für ältere Versionen bleibt es wie bisher
			registerReceiver(m_bcReceiver, filter);
		}
	}

	// unregister the receiver
	@Override
	protected void onPause()
	{
		super.onPause();
		unregisterReceiver(m_bcReceiver);
	}
//endregion

// -------------------------------------------------------------------------------------------------
// region Notifications
// -------------------------------------------------------------------------------------------------
	@Override
	protected void onNewIntent(Intent intent)
	{
		super.onNewIntent(intent);
		setIntent( intent );
		// Hier landet der Benutzer, wenn die App schon offen war
		handleNotificationClick(intent);
	}

	private void handleNotificationClick(Intent intent)
	{
		if (intent != null && intent.getBooleanExtra(GpsService.FROM_NOTIFICATION, false))
		{
			onNotificationClick();
		}
	}
	protected void onNotificationClick()
	{}
//endregion

// -------------------------------------------------------------------------------------------------
// region Permissions
// -------------------------------------------------------------------------------------------------
	private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
	private static final int STORAGE_PERMISSION_REQUEST_CODE = 1002;

	/**
	 * Check if the location permission is granted
	 * @return true if the location permission is granted, false otherwise
	 */
	public boolean checkNotificationPermission()
	{
		return NotificationManagerCompat.from(this).areNotificationsEnabled();
	}

	/**
	 * Open the notification permission
	 */
	public void openNotificationSettings()
	{
		Intent intent = new Intent();
		// Direkt zu den Benachrichtigungskanälen der App
		intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
		intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
		startActivity(intent);
	}

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
/*
		if (Build.VERSION.SDK_INT >= 33)
		{ // Android 13+
			if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
			{
				requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
			}
		}
 */
		return true;
	}

	/**
	 * Prüft, ob für die gespeicherte SAF-Uri noch Rechte vorliegen.
	 */
	private  boolean checkSafFolderPermissions( boolean writePermission )
	{
		String uriString = getSharedPreferences(CONFIG_FILE, MODE_PRIVATE).getString(CONFIG_KEY, null);
		if(uriString == null)
		{
			return false;
		}
		Uri treeUri = Uri.parse(uriString);
		if(treeUri == null)
		{
			return false;
		}

		int modeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
		if(writePermission)
		{
			modeFlags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
		}

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
		if(checkIsExternalStorageManager())
		{
			return true;
		}
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
			if(hasStorageFolder())
			{
				return RequestCode.OK;
			}

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
					showMessage(
						0,
						getAppName(),
						"Fine Location Permission Missing!",
						true,
						null
					);
				}
				else
				{
					finish();
				}
			}
		}
	}
//endregion

// -------------------------------------------------------------------------------------------------
// region Basic Activity implementation
// -------------------------------------------------------------------------------------------------

	private LocationManager		m_locationManager = null;
	private GnssStatus.Callback	m_gnssStatusListener = null;

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
		startGpsService();

		// Acquire a reference to the system Location Manager
		m_locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

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

		handleNotificationClick(getIntent());
	}

	@Override
	protected void onDestroy()
	{
		if( m_locationManager != null )
		{
			m_locationManager.unregisterGnssStatusCallback(m_gnssStatusListener);
		}
		//stopGpsService();
		super.onDestroy();
	}
//endregion

// -------------------------------------------------------------------------------------------------
// region Storage Access Framework (SAF)
// -------------------------------------------------------------------------------------------------
	private static final int REQUEST_CODE_OPEN_DIRECTORY = 1234;
	static final String CONFIG_FILE = "prefs";
	static final String CONFIG_KEY = "storage_folder_uri";

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
					if( isServiceBound())
					{
						m_service.getGpsLogger().setUriString(uriString);
					}

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
	 * by remove the selected saf folder from the shared preferences
	 */
	public void selectPublicFolder()
	{
		getSharedPreferences(CONFIG_FILE, MODE_PRIVATE)
			.edit()
			.remove(CONFIG_KEY)
			.apply();
		if( isServiceBound() )
		{
			getService().getGpsLogger().setUriString(null);
		}
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
//endregion

// -------------------------------------------------------------------------------------------------
// region Basic GPS handling
// -------------------------------------------------------------------------------------------------
	private boolean m_gpsEnabled = false;
	protected boolean isGpsEnabled()
	{
		return m_gpsEnabled;
	}

	protected abstract void onLocationEnabled();
	protected abstract void onLocationDisabled();
	protected abstract void onGnssStatusChanged2(int event, GnssStatus status);
	protected abstract void onLocationChanged( Location newLocation );

	/**
	 * Simulate a location fix
	 * @param newLocation the location to simulate
	 */
	protected void simulateLocationFix( @NonNull Location newLocation)
	{
		if( isServiceBound() )
		{
			m_service.getGpsReceiver().lockLocationChanged(newLocation, false, this::onLocationChanged);
		}
	}
//endregion

// -------------------------------------------------------------------------------------------------
// region Interface to the Service
// -------------------------------------------------------------------------------------------------
	/**
	 * Get the extended GPS status
	 * @return true if extended GPS is enabled, false otherwise
	 */
	public boolean isExtendedGpsEnabled()
	{
		return isServiceBound() && m_service.isExtendedGpsEnabled();
	}

	/**
	 * Check if calibration is enabled
	 * @return true if calibration is enabled, false otherwise
	 */
	public boolean getCalibration()
	{
		return isServiceBound() && m_service.getCalibration();
	}

	/**
	 * get the track GPS flag
	 * @return true, if we are logging the track points
	 */
	public boolean getTrackGps()
	{
		return isServiceBound() && m_service.getGpsLogger().getTrackGps();
	}

	/**
	 * Get the GPS interval
	 * @return the interval in milliseconds
	 */
	public int getInterval()
	{
		return isServiceBound() ? m_service.getInterval() : 0;
	}

	/**
	 * Check if a location is available
	 * @return true if a location is available, false otherwise
	 */
	public boolean hasLocation()
	{
		return isServiceBound() && m_service.hasLocation();
	}

	/**
	 * Get the last location
	 * @return the last location null if no location is available
	 */
	public Location lastLocation()
	{
		if( isServiceBound() )
			return m_service.lastLocation();
		return null;
	}

	/**
	 * Check if the accuracy of a GPS fix is ignored
	 * @return the value
	 */
	public boolean getIgnoreAccuracy()
	{
		return isServiceBound() && m_service.getIgnoreAccuracy();
	}

	/**
	 * Set if the accuracy of a GPS fix should be ignored
	 * @param ignoreAcuracy the value
	 */
	public void setIgnoreAccuracy(boolean ignoreAcuracy)
	{
		if( isServiceBound() )
			m_service.setIgnoreAccuracy( ignoreAcuracy );
	}

	/**
	 * Get the accuracy
	 * @return the accuracy of the last gps fix
	 */
	public double getAccuracy()
	{
		return isServiceBound() ? m_service.getAccuracy() : 0;
	}

	/**
	 * Get the number of locations in the current buffer
	 * @return the num of locations
	 */
	public int getNumLocations()
	{
		return isServiceBound() ? m_service.getNumLocations() : 0;
	}

	/**
	 * Get the current bearing
	 * @return the current bearing
	 */
	public double getCurBearing()
	{
		return isServiceBound() ? m_service.getCurBearing() : 0;
	}

	/**
	 * Get the current speed
	 * @return the current speed in m/s
	 */
	public double getSpeed()
	{
		return isServiceBound() ? m_service.getSpeed() : 0;
	}

	/**
	 * Get the current acceleration
	 * @return the current acceleration in m/s^2
	 */
	public double getAccel()
	{
		return isServiceBound() ? m_service.getAccel() : 0;
	}

	/**
	 * Get the current acceleration as a string for the UI
	 * @return the current acceleration as a string
	 */
	public String getAccelStr()
	{
		return isServiceBound() ? m_service.getAccelStr() : "";
	}

	/**
	 * Get the resolution if the GPS receiver
	 * @return the resolution
	 */
	public double getResolution()
	{
		return isServiceBound() ? m_service.getResolution() : 0;
	}

	/**
	 * Get the brake time (the time reducing the current speed=
	 * @return the time in milliseconds
	 */
	public long getBrakeTime()
	{
		return isServiceBound() ? m_service.getBrakeTime() : 0;
	}

	/**
	 * Resets the measuring of the brake time
	 * Set the beake time (the time reducing the current speed=
	 * @param brakeTime the time in milliseconds
	 */
	public void setBrakeTime( long brakeTime )
	{
		if( isServiceBound() )
			m_service.setBrakeTime(brakeTime);
	}
//endregion

	// may be this is useful
	//public boolean isDarkModeActive(Context context)
	//{
	//	int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
	//
	//	return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
	//}
}

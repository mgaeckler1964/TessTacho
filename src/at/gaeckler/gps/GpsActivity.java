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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.locks.ReentrantLock;

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
	private long	m_locationFixCount = 0;

	/**
	 * Check the calibration mode
	 * @return true if calibration is active, false otherwise
	 */
	public boolean isCalibrationMode()
	{
		return m_calibration;
	}

	/**
	 * Activate the calibration mode if not yet done
	 */
	public void enableCalibration()
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

	/**
	 * Deactivate the calibration mode
	 */
	public void disableCalibration()
	{
		m_calibration = false;
	}

	/**
	 * Get the calibrated location
	 * @param provider the provider to use
	 * @return a calibrated location that is the mean of all locations
	 */
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

	/**
	 * Get the number of location fixes
	 * @return the number of location fixes
	 */
	public long getLocationFixCount()
	{
		return m_locationFixCount;
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

		outState.putLong(FIX_COUNT_KEY, m_locationFixCount);
		outState.putBoolean(CALIBRATION_KEY, m_calibration);
		outState.putDouble(SUM_LONGITUDE_KEY, m_sumLongitude);
		outState.putDouble(SUM_LATITUDE_KEY, m_sumLatitude);
		outState.putDouble(SUM_ALTITUDE_KEY, m_sumAltitude);
	}

	@Override
	protected void onDestroy()
	{
		if( m_locationManager != null )
		{
			m_locationManager.removeUpdates(m_locationListener);
			m_locationManager.unregisterGnssStatusCallback(m_gnssStatusListener);
		}
		try
		{
			closeXMLos();
			closeRAWfileOS();
		}
		catch(IOException e)
		{
			Log.e(getLocalClassName(), "closeXMLos or closeRAWfileOS failed", e);
		}
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
					getSharedPreferences(CONFIG_FILE, MODE_PRIVATE)
							.edit()
							.putString(CONFIG_KEY, treeUri.toString())
							.apply();

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
		Any file
	-----------------------------------------------------------------------------------------------
	 */
	private  File getExternalFile(boolean pub, String fileName )
	{
		File dir = pub
				? Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
				: getExternalFilesDir(null);

		assert dir != null;
		if( !dir.exists() )
		{
			dir.mkdir();
		}
		File file = new File(dir, fileName);

		return file;
	}

	/**
	 * Open a file for reading
	 * @param pub if true, the file is in the public directory, otherwise in the private directory
	 * @param filename the filename to use
	 * @return the input stream
	 * @throws IOException in case of an IO error
	 */
	public InputStream openInputStream(boolean pub, String filename) throws IOException
	{
		String uriString = getSharedPreferences(CONFIG_FILE, MODE_PRIVATE).getString(CONFIG_KEY, null);

		if (uriString != null)
		{
			try
			{
				// MODERNER WEG (Vorbereitung)
				Uri treeUri = Uri.parse(uriString);
				DocumentFile root = DocumentFile.fromTreeUri(this, treeUri);
				DocumentFile file = root.findFile(filename);

				if (file != null && file.exists())
				{
					return getContentResolver().openInputStream(file.getUri());
				}
			}
			catch (Exception e)
			{
				Log.e(getLocalClassName(), "SAF failed, trying legacy", e);
			}
		}

		// fall back for old androids
		File file = getExternalFile(pub, filename);
		return new FileInputStream(file);
	}

	/**
	 * Open a file for reading in a public directory
	 * @param filename the filename to read
	 * @return the input stream
	 * @throws IOException in case of an IO error
	 */
	public InputStream openInputStream( String filename) throws IOException
	{
		return openInputStream(true, filename);
	}

	/**
	 * Open a file for writing
	 * @param pub if true, the file is in the public directory, otherwise in the private directory
	 * @param filename the filename to use
	 * @param append if true, the file is opened in append mode, otherwise in write mode
	 * @return the output stream
	 * @throws IOException in case of an IO error
	 */
	public OutputStream openOutputStream(boolean pub, String filename, boolean append) throws IOException
	{
		String uriString = getSharedPreferences(CONFIG_FILE, MODE_PRIVATE).getString(CONFIG_KEY, null);
		if (uriString != null)
		{
			try
			{
				Uri treeUri = Uri.parse(uriString);
				DocumentFile root = DocumentFile.fromTreeUri(this, treeUri);
				DocumentFile file = root.findFile(filename);
				if (file == null)
				{
					String mime;

					if( filename.endsWith(".gpx") )
						mime = "application/gpx+xml";
					else if( filename.endsWith(".xml") )
						mime = "text/xml";
					else if( filename.endsWith(".txt") )
						mime = "text/plain";
					else
						mime = "application/octet-stream";

					file = root.createFile(mime, filename);
				}
				if( file != null && file.exists())
				{
					// "wa" steht für Write-Append
					return getContentResolver().openOutputStream(file.getUri(), append ? "wa" : "w");
				}
			}
			catch (java.lang.Exception e)
			{
				Log.e(getLocalClassName(), "SAF write failed", e);
			}
		}

		// fall back for old androids
		File file = getExternalFile(pub, filename);
		return new FileOutputStream(file, append);
	}
	/**
	 * Open a file for writing in a public directory
	 * @param filename the filename to use
	 * @param append if true, the file is opened in append mode, otherwise in write mode
	 * @return the output stream
	 * @throws IOException in case of an IO error
	 */
	public OutputStream openOutputStream(String filename, boolean append) throws IOException
	{
		return openOutputStream(true, filename, append);
	}

	private void deleteLocalFile(String filename)
	{
		File file = getExternalFile(true, filename);
		if( file != null && file.exists() )
		{
			if( file.delete() )
				return;
		}

		String uriString = getSharedPreferences(CONFIG_FILE, MODE_PRIVATE).getString(CONFIG_KEY, null);

		if( uriString != null )
		{
			try
			{
				Uri treeUri = Uri.parse(uriString);
				DocumentFile root = DocumentFile.fromTreeUri(this, treeUri);
				DocumentFile dFile = root.findFile(filename);

				if( dFile != null && dFile.exists() )
				{
					if( dFile.delete() )
					{
						Log.d("GPS", "File deleted via SAF: " + filename);
					}				}
			}
			catch (Exception e)
			{
				Log.e(getLocalClassName(), "SAF failed, trying legacy", e);
			}
		}
	}

	/*
	-----------------------------------------------------------------------------------------------
		RAW file
	-----------------------------------------------------------------------------------------------
	 */
	// used for debugging
	private Boolean				m_logRaw = false;
	private OutputStream		m_rawFileOS = null;
	private PrintWriter			m_rawPos = null;
	private static final String RAW_TRACK_FILE = ".temp.raw.gps.txt";

	private String getRawTrackFileName()
	{
		return getLocalClassName() + RAW_TRACK_FILE;
	}

	private void openRAWfileOS() throws IOException
	{
		m_rawFileOS = openOutputStream(getRawTrackFileName(), true);
		m_rawPos = new PrintWriter(
			new BufferedWriter(new OutputStreamWriter(m_rawFileOS))
		);
	}

	private void closeRAWfileOS() throws IOException
	{
		if( m_rawPos != null )
		{
			m_rawPos.close();
			m_rawPos = null;
		}
		if( m_rawFileOS != null )
		{
			m_rawFileOS.close();
			m_rawFileOS = null;
		}

	}

	private void appendTrackPoint(Location loc)
	{
		if( !m_logRaw || !checkWriteStoragePermission() )
		{
			return;
		}
		try
		{
			if( m_rawPos == null )
			{
				openRAWfileOS();
			}
			m_rawPos.println(locationString(loc, true));
		}
		catch( Exception e)
		{
			// ignore
		}
	}

	/**
	 * Read the track points from the file
	 */
	public void readTrackPoints()
	{
		if(!checkReadStoragePermission())
		{
			return;
		}

		try
		{
			InputStream is = openInputStream(getRawTrackFileName());
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));

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
					//System.out.println("I'm in");
				}
*/
				if( newLocation != null )
				{
					lockLocationChanged(newLocation, false);
				}
			}

			reader.close();
			m_logRaw = true;
		}
		catch (IOException e)
		{
			Log.e(getLocalClassName(), "exception in readTrackPoints", e);
		}

	}

	/*
	-----------------------------------------------------------------------------------------------
		XML file
	-----------------------------------------------------------------------------------------------
	 */
	private static final String XML_TRACK_FILE = ".temp.gpx";
	private OutputStream	m_xmlFileOS = null;
	private PrintWriter		m_xmlPos = null;

	private Location		m_lastTrackPoint = null;
	float					m_lastBearing=0;

	private String getXmlTrackFileName()
	{
		return getLocalClassName() + XML_TRACK_FILE;
	}
	private void openXMLos() throws IOException
	{
		m_xmlFileOS = openOutputStream(getXmlTrackFileName(), true);
		m_xmlPos = new PrintWriter(
			new BufferedWriter(new OutputStreamWriter(m_xmlFileOS))
		);
	}

	private void closeXMLos() throws IOException
	{
		if( m_xmlPos != null )
		{
			m_xmlPos.close();
			m_xmlPos = null;
		}
		if( m_xmlFileOS != null )
		{
			m_xmlFileOS.close();
			m_xmlFileOS = null;
		}

	}

	/**
	 * Append a track point to the XML file in GPX format
	 * @param loc the location to append
	 */
	public void appendTrackPoint2XML(Location loc)
	{
		try
		{
			if( m_xmlPos == null )
			{
				openXMLos();
			}
			m_xmlPos.write("<trkpt lon=\"");
			m_xmlPos.print(loc.getLongitude());
			m_xmlPos.write("\" lat=\"");
			m_xmlPos.print(loc.getLatitude());
			m_xmlPos.write("\">\n");
			m_xmlPos.write("\t<ele>");
			m_xmlPos.print(getCorrectedAltitude(loc));
			m_xmlPos.write("</ele>\n");
			m_xmlPos.write("\t<geoidheight>");
			m_xmlPos.print(loc.getAltitude());
			m_xmlPos.write("</geoidheight>\n");
			m_xmlPos.write("\t<time>");
			m_xmlPos.print(getDateLoc(loc, true));
			m_xmlPos.write("</time>\n");

			m_xmlPos.write("\t<extensions>\n");

			m_xmlPos.write("\t\t<gak:utcStamp>");
			m_xmlPos.print(loc.getTime());
			m_xmlPos.write("</gak:utcStamp>\n");
			m_xmlPos.write("\t\t<gak:speed>");
			m_xmlPos.print(loc.getSpeed());
			m_xmlPos.write("</gak:speed>\n");
			if( m_lastTrackPoint == null )
			{
				m_lastTrackPoint = loc;
			}
			else
			{
				m_xmlPos.write("\t\t<gak:calculated>\n");

				float bearing = m_lastTrackPoint.bearingTo(loc);
				m_xmlPos.write("\t\t\t<gak:bearing>");
				m_xmlPos.print(bearing);
				m_xmlPos.write("</gak:bearing>\n");

				m_xmlPos.write("\t\t\t<gak:turn>");
				m_xmlPos.print(bearing-m_lastBearing);
				m_xmlPos.write("</gak:turn>\n");

				float distance = m_lastTrackPoint.distanceTo(loc);
				m_xmlPos.write("\t\t\t<gak:distance>");
				m_xmlPos.print(distance);
				m_xmlPos.write("</gak:distance>\n");

				long elapsedTime = loc.getTime()-m_lastTrackPoint.getTime();
				m_xmlPos.write("\t\t\t<gak:elapsedTime>");
				m_xmlPos.print(elapsedTime);
				m_xmlPos.write("</gak:elapsedTime>\n");

				if(elapsedTime>0)
				{
					m_xmlPos.write("\t\t\t<gak:speed>");
					m_xmlPos.print(distance/(elapsedTime/1000.0));
					m_xmlPos.write("</gak:speed>\n");
				}
				m_xmlPos.write("\t\t</gak:calculated>\n");


				m_lastBearing = bearing;
				m_lastTrackPoint = loc;
			}
			m_xmlPos.write("\t</extensions>\n");
			m_xmlPos.write("</trkpt>\n");
		}
		catch( Exception e)
		{
			// ignore
		}
	}

	/**
	 * Create a GPX file from the track points
	 * @throws IOException in case of an IO error
	 */
	public void createGpxTrack() throws IOException
	{
		try
		{
			closeXMLos();
		}
		catch( Exception e )
		{
			// ignore
		}

		String xmlTrackName = getXmlTrackFileName();
		String fnName = getDateLong(m_startTime, false);
		String gpxFileName = fnName + ".gpx";
		try(
			InputStream is = openInputStream(xmlTrackName);
			BufferedReader  reader = new BufferedReader(new InputStreamReader(is))
		)
		{
			OutputStream os = openOutputStream(gpxFileName, false);
			PrintWriter writer = new PrintWriter(os);
			String appName = getString(getApplicationInfo().labelRes);
			writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\n");
			writer.write("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:gak=\"http://www.gaeckler.at/GPXEXT/1/0\" creator=\"" + appName + "\" version=\"1.1\">\n");
			writer.write("<metadata>\n");
			writer.write("<name>gpxFile" + fnName + "</name>\n");
			writer.write("<descr>Gpx Created with " + appName + " for Android</descr>\n");
			writer.write("<author><name>GAK</name></author>\n");
			writer.write("</metadata>\n");

			writer.write("<trk>\n");
			writer.write("<name>Track" + fnName + "</name>\n");
			writer.write("<descr>Track Created with " + appName + " for Android</descr>\n");
			writer.write("<trkseg>\n");
			while(true)
			{
				String line = reader.readLine();
				if(line == null)
				{
					break;
				}
				writer.println(line);
			}
			writer.write("</trkseg>\n");
			writer.write("</trk>\n");
			writer.write("</gpx>\n");

			writer.close();
			reader.close();

			deleteLocalFile(xmlTrackName);

			///  TODO analyze the usage, This code is from GpxMotorCycle
			// reset
//			m_distance = 0;
//			m_distanceLocation = null;
//			m_upMeter = 0;
//			m_downMeter = 0;
			m_startTime = 0;
//			m_minAccel = 0;
//			m_maxAccel = 0;
//			m_maxSpeed = 0;
			setBrakeTime(0);
		}
	}

	/*
	-----------------------------------------------------------------------------------------------
		Date Time Format
	-----------------------------------------------------------------------------------------------
	 */
	private SimpleDateFormat	m_sdfIso = null;

	private SimpleDateFormat getIsoDateFormat()
	{
		if( m_sdfIso == null )
		{
			m_sdfIso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
			m_sdfIso.setTimeZone(TimeZone.getTimeZone("UTC"));
		}
		return m_sdfIso;
	}

	private SimpleDateFormat	m_sdfFname = null;

	private SimpleDateFormat getFnameDateFormat()
	{
		if( m_sdfFname == null )
		{
			m_sdfFname = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
			m_sdfFname.setTimeZone(TimeZone.getTimeZone("UTC"));
		}
		return m_sdfFname;
	}

	private String getDateDate( Date date, boolean useIso )
	{
		return (useIso ? getIsoDateFormat() : getFnameDateFormat()).format(date);
	}

	private String getDateLong( long timeStamp, boolean useIso )
	{
		return getDateDate(new Date(timeStamp), useIso);
	}

	private String getDateLoc( Location loc, boolean useIso )
	{
		return getDateLong(loc.getTime(), useIso);
	}

	/*
	-----------------------------------------------------------------------------------------------
		Basic GPS handling
	-----------------------------------------------------------------------------------------------
	 */
	private CountDownTimer		m_gpsTimer = null;
	private int					m_gpsInterval = 0;
	private static final double	MAX_SPEED = 100;
	private static final double	MAX_ACCEL = 100;

	public abstract void onLocationEnabled();
	public abstract void onLocationDisabled();
	public abstract void onGnssStatusChanged2(int event, GnssStatus status);
	public abstract void onLocationChanged( Location newLocation );

	// correction valid for Linz/Austria
	/**
	 * Get the corrected sealevel altitude
	 * @param loc the location
	 * @return the corrected sealevelaltitude
	 */
	static public int getCorrectedAltitude( Location loc )
	{
		return (int)loc.getAltitude()-50;
	}

	/**
	 * set the sealevel altitude
	 * @param loc the location
	 * @param altitude the sealevel altitude
	 */
	static public void setCorrectedAltitude( Location loc, double altitude )
	{
		loc.setAltitude(altitude+50);
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

	static private double getElapsedTime(Location loc1, Location loc2)
	{
		return (double)(loc2.getTime()-loc1.getTime())/1000.0;
	}

	static private double getSpeed(Location loc1, Location loc2)
	{
		return (double)loc1.distanceTo(loc2) / getElapsedTime(loc1, loc2);
	}

	static private double getAccel(Location loc1, Location loc2)
	{
		return (double)(loc2.getSpeed()-loc1.getSpeed()) / getElapsedTime(loc1, loc2);
	}

	private final ReentrantLock		m_lock = new ReentrantLock();
	private Location[]				m_lastLocations;
	private boolean					m_goodGps = false;
	private long					m_startTime = 0;

	private void lockLocationChanged( @NonNull Location newLocation, boolean fromGPS )
	{
		if( !fromGPS
		|| (
			null != newLocation.getProvider()
			&& newLocation.getProvider().equalsIgnoreCase(LocationManager.GPS_PROVIDER) )
		)
		{
			m_lock.lock();
			try
			{
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

	/**
	 * Simulate a location fix
	 * @param newLocation the location to simulate
	 */
	protected void simulateLocationFix( @NonNull Location newLocation)
	{
		lockLocationChanged( newLocation, false );
	}

	/*
	-----------------------------------------------------------------------------------------------
		(de)serialization of a location to a string
	-----------------------------------------------------------------------------------------------
	 */
	protected static final String	NAME_KEY = "name";

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

	/**
	 * serializes a location to a string
	 * @param src the location to convert
	 * @return the string representation of the location
	 */
	public static String locationString( @NonNull Location src )
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

	/**
	 * Deserializes a location from a string
	 * @param src the string representation of the location
	 * @return the location
	 */
	public static Location locationString( String src )
	{
		return locationString( src, false );
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

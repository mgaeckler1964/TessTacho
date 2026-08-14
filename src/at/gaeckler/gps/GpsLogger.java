/*
		Project:		GPS
		Module:			GpsLogger.java
		Description:	The logger for all GPS positions
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

import android.content.Context;
import android.location.Location;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

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

public class GpsLogger
{
	private final Context m_context;
	private String m_uriString;

	GpsLogger(Context context, String uriString)
	{
		m_context = context.getApplicationContext();
		m_uriString = uriString;
	}

	/*
	-----------------------------------------------------------------------------------------------
		Basic Activity implementation
	-----------------------------------------------------------------------------------------------
	 */

	public void onStop()
	{
		if( m_xmlPos != null )
		{
			m_xmlPos.flush();
		}
		if( m_rawPos != null )
		{
			m_rawPos.flush();
		}
	}

	protected void onDestroy()
	{
		try
		{
			closeXMLos();
			closeRAWfileOS();
		}
		catch(IOException e)
		{
			Log.e(m_context.getPackageName(), "closeXMLos or closeRAWfileOS failed", e);
		}
	}

	/*
		-----------------------------------------------------------------------------------------------
			Any file
		-----------------------------------------------------------------------------------------------
	 */

	/**
	 * change the URI string
	 * @param uriString the new uri for the selected directory or null if we should use the external
	 *                     document directory
	 */
	public void setUriString(String uriString)
	{
		m_uriString = uriString;
	}

	private String getAppName()
	{
		try
		{
			// Holt das Label der App (app_name aus dem Manifest)
			return m_context.getPackageManager()
					.getApplicationLabel(m_context.getApplicationInfo())
					.toString().replace(" ", "_");
		}
		catch (Exception e)
		{
			return m_context.getPackageName();
		}
	}

	private File getExternalFile(boolean pub, String fileName )
	{
		File dir = pub
				? Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
				: m_context.getExternalFilesDir(null);

		assert dir != null;

		if( !dir.exists() )
		{
			dir.mkdir();
		}

		String appDir = getAppName();
		dir = new File(dir, appDir);
		if( !dir.exists() )
		{
			dir.mkdir();
		}

		return new File(dir, fileName);
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
		if( pub && m_uriString != null )
		{
			try
			{
				// MODERNER WEG (Vorbereitung)
				Uri treeUri = Uri.parse(m_uriString);
				DocumentFile root = DocumentFile.fromTreeUri(m_context, treeUri);
				DocumentFile file = root.findFile(filename);

				if (file != null && file.exists())
				{
					return m_context.getContentResolver().openInputStream(file.getUri());
				}
			}
			catch (Exception e)
			{
				Log.e(m_context.getPackageName(), "SAF failed, trying legacy", e);
			}
		}

		// fall back for old androids and private storage
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
		if( pub && m_uriString != null )
		{
			if( !append )
			{
				deleteLocalFile(filename);
			}
			try
			{
				Uri treeUri = Uri.parse(m_uriString);
				DocumentFile root = DocumentFile.fromTreeUri(m_context, treeUri);
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
					return m_context.getContentResolver().openOutputStream(file.getUri(), append ? "wa" : "wt");
				}
			}
			catch (java.lang.Exception e)
			{
				Log.e(m_context.getPackageName(), "SAF write failed", e);
			}
		}

		// fall back for old androids or private storage
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
		if( file.exists() && file.delete())
		{
			return;
		}

		if( m_uriString != null )
		{
			try
			{
				Uri treeUri = Uri.parse(m_uriString);
				DocumentFile root = DocumentFile.fromTreeUri(m_context, treeUri);
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
				Log.e(m_context.getPackageName(), "SAF failed, trying legacy", e);
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
	private PrintWriter m_rawPos = null;
	private static final String RAW_TRACK_FILE = ".temp.raw.gps.txt";

	/**
	 * get the log flag
	 * @return true, if we are logging the raw location points for replay at next start
	 */
	public Boolean getLogRaw()
	{
		return m_logRaw;
	}

	private String getRawTrackFileName()
	{
		return m_context.getPackageName() + RAW_TRACK_FILE;
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

	/**
	 * Append a track point to the RAW file
	 * @param loc the location to append
	 */
	public  void appendTrackPoint(Location loc)
	{
		if( !m_logRaw )
		{
			return;
		}
		try
		{
			if( m_rawPos == null )
			{
				openRAWfileOS();
			}
			m_rawPos.println(GpsUtils.locationString(loc, true));
		}
		catch( Exception e)
		{
			// ignore
		}
	}

	/**
	 * Read the track points from the file
	 * TODO: This code is from GpxMotorCycle check the read permissions before calling
	 */

	public void readTrackPoints()
	{
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
				Location newLocation = GpsUtils.locationString(line,true);

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
					// FIX this code is for GpxMotorCycle
					// lockLocationChanged(newLocation, false);
				}
			}

			reader.close();
			m_logRaw = true;
		}
		catch (IOException e)
		{
			Log.e(m_context.getPackageName(), "exception in readTrackPoints", e);
		}
	}

	/*
	-----------------------------------------------------------------------------------------------
		XML file
	-----------------------------------------------------------------------------------------------
	 */
	private static final String XML_TRACK_FILE = ".temp.gpx";
	private boolean			m_trackGps = false;
	private long			m_startTime = 0;
	private long			m_locationFixCount = 0;
	private OutputStream	m_xmlFileOS = null;
	private PrintWriter		m_xmlPos = null;

	private Location		m_lastTrackPoint = null;
	private float			m_lastBearing=0;

	/**
	 * restart the track after app restart
	 * @param track if true, we are logging the track points
	 * @param startTime the start time to restore
	 */
	public void restartTrack( boolean track, long startTime)
	{
		m_trackGps = track;
		m_startTime = startTime;
	}

	/**
	 * start the track
	 */
	public void startTrack()
	{
		m_trackGps = true;
		m_startTime = 0;
		m_locationFixCount = 0;
	}

	/**
	 * stop the track without saving it
	 */
	public void stopTrack()
	{
		m_trackGps = false;
		m_startTime = 0;
		m_locationFixCount = 0;
	}

	/**
	 * get the track GPS flag
	 * @return true, if we are logging the track points
	 */
	public boolean getTrackGps()
	{
		return m_trackGps;
	}

	/**
	 * get the track start time
	 *
	 * @return the time when we have started logging
	 */
	public long getTrackGpsStart()
	{
		return m_startTime;
	}

	/**
	 * get the number of location fixes
	 * @return the number of location fixes
	 */
	public long getLocationFixCount()
	{
		return m_locationFixCount;
	}
	private String getXmlTrackFileName()
	{
		return m_context.getPackageName() + XML_TRACK_FILE;
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

	void appendTrackPoint2XML(Location loc)	// package access only
	{
		try
		{
			if( m_startTime == 0 )
				m_startTime = loc.getTime();
			++m_locationFixCount;

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
			m_xmlPos.print(GpsUtils.getCorrectedAltitude(loc));
			m_xmlPos.write("</ele>\n");
			m_xmlPos.write("\t<geoidheight>");
			m_xmlPos.print(loc.getAltitude());
			m_xmlPos.write("</geoidheight>\n");
			m_xmlPos.write("\t<time>");
			m_xmlPos.print(GpsUtils.getDateLoc(loc, true));
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
		String fnName = GpsUtils.getDateLong(m_startTime, false);
		String gpxFileName = fnName + ".gpx";
		try(
				InputStream is = openInputStream(xmlTrackName);
				BufferedReader  reader = new BufferedReader(new InputStreamReader(is))
		)
		{
			OutputStream os = openOutputStream(gpxFileName, false);
			PrintWriter writer = new PrintWriter(os);
			String appName = m_context.getString(m_context.getApplicationInfo().labelRes);
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
			m_trackGps = false;
//			m_minAccel = 0;
//			m_maxAccel = 0;
//			m_maxSpeed = 0;
//			setBrakeTime(0);
		}
	}
}

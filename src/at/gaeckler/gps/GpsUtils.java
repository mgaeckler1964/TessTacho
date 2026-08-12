/*
		Project:		GPS
		Module:			GpsUtils.java
		Description:	Some useful utils GPS
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

import android.location.Location;
import android.os.Bundle;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class GpsUtils
{
	/*
	-----------------------------------------------------------------------------------------------
		some useful tools for loactions
	-----------------------------------------------------------------------------------------------
	 */

	/**
	 * Calculate the elapsed time between two locations
	 * @param loc1 start location
	 * @param loc2 end location
	 * @return time in seconds
	 */
	static public double getElapsedTime(Location loc1, Location loc2)
	{
		return (double)(loc2.getTime()-loc1.getTime())/1000.0;
	}

	/**
	 * Calculate the speed between two locations
	 * @param loc1 start location
	 * @param loc2 end location
	 * @return speed in m/s
	 */
	static public double getSpeed(Location loc1, Location loc2)
	{
		return (double)loc1.distanceTo(loc2) / getElapsedTime(loc1, loc2);
	}

	/**
	 * Calculate the acceleration between two locations
	 * @param loc1 start location
	 * @param loc2 end location
	 * @return speed in m/s²
	 */
	static public double getAccel(Location loc1, Location loc2)
	{
		return (double)(loc2.getSpeed()-loc1.getSpeed()) / getElapsedTime(loc1, loc2);
	}

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

	/*
	-----------------------------------------------------------------------------------------------
		Date Format
	-----------------------------------------------------------------------------------------------
	 */

	private static final SimpleDateFormat m_sdfIso = createIsoDateFormat();

	private static SimpleDateFormat createIsoDateFormat()
	{
		SimpleDateFormat  sdfIso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
		sdfIso.setTimeZone(TimeZone.getTimeZone("UTC"));
		return sdfIso;
	}

	public static SimpleDateFormat getIsoDateFormat()
	{
		return m_sdfIso;
	}

	private static final SimpleDateFormat	m_sdfFname = createFnameDateFormat();

	private static SimpleDateFormat createFnameDateFormat()
	{
		SimpleDateFormat  sdfFname = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
		sdfFname.setTimeZone(TimeZone.getTimeZone("UTC"));
		return sdfFname;
	}
	public static SimpleDateFormat getFnameDateFormat()
	{
		return m_sdfFname;
	}

	public static String getDateDate(Date date, boolean useIso )
	{
		return (useIso ? getIsoDateFormat() : getFnameDateFormat()).format(date);
	}

	public static String getDateLong( long timeStamp, boolean useIso )
	{
		return getDateDate(new Date(timeStamp), useIso);
	}

	public static String getDateLoc(Location loc, boolean useIso )
	{
		return getDateLong(loc.getTime(), useIso);
	}

	/*
	-----------------------------------------------------------------------------------------------
		(de)serialization of a location to a string
	-----------------------------------------------------------------------------------------------
	 */
	public static final String	NAME_KEY = "name";

	public static String locationString( Location src, boolean raw )
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

	public static Location locationString( String src, boolean raw )
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

}

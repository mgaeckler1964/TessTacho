/*
		Project:		GPS
		Module:			GpsProcessor.java
		Description:	The common location processor for all GPS apps
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

import java.text.DecimalFormatSymbols;
import java.util.LinkedList;
import java.util.Queue;

import android.location.Location;

public class GpsProcessor
{
	private static final int MAX_AGE_MS = 5000;
	private static final int MIN_BEARING_COUNT = 2;
	private static final int MIN_LOCATION_COUNT = 20;

	private boolean					m_ignoreAccuracy = false;
	private double					m_accuracy = 0.0;
	final private Queue<Location>	m_locationList = new LinkedList<>();
	private long					m_brakeTime = 0;
	private Location				m_startBrake = null;
	private double					m_curBearing = 0;
	private double					m_speed = 0;
	private double					m_accel = 0;
	private String					m_accelStr = "";
	private double					m_resolution = 99999999;

	/**
	 * Get the brake time (the time reducing the current speed=
	 * @return the time in milliseconds
	 */
	public long getBrakeTime()
	{
		return m_brakeTime + (
			m_startBrake != null ? lastLocation().getTime()-m_startBrake.getTime()
								 :0);
	}

	/**
	 * Resets the measuring of the brake time
	 * Set the brake time (the time reducing the current speed=
	 * @param brakeTime the time in milliseconds
	 */
	public void setBrakeTime( long brakeTime )
	{
		m_brakeTime = brakeTime;
		m_startBrake = null;
	}

	/**
	 * Get the current bearing
	 * @return the current bearing
	 */
	public double getCurBearing()
	{
		return m_curBearing;
	}

	/**
	 * Get the current speed
	 * @return the current speed in m/s
	 */
	public double getSpeed()
	{
		return m_speed;
	}

	/**
	 * Convert a speed in m/s to km/h
	 * @param speedMs the speed in m/s
	 * @return the speed in km/h
	 */
	public static long speedToKmh( double speedMs )
	{
		return (long)(speedMs * 3.6 + 0.5);
	}

	/**
	 * Convert a speed in km/h to m/s
	 * @param speedKmh the speed in km/h
	 * @return the speed in m/s
	 */
	public static double speedToMs( long speedKmh )
	{
		return (double)speedKmh / 3.6;
	}

	/**
	 * Get the current acceleration
	 * @return the current acceleration in m/s^2
	 */
	public double getAccel()
	{
		return m_accel;
	}

	/**
	 * Get the current acceleration as a string for the UI
	 * @return the current acceleration as a string
	 */
	public String getAccelStr()
	{
		return m_accelStr;
	}

	/**
	 * Get the accuracy
	 * @return the accuracy of the last gps fix
	 */
	public double getAccuracy()
	{
		return m_accuracy;
	}

	/**
	 * Check if the accuracy of a GPS fix is ignored
	 * @return the value
	 */
	public boolean getIgnoreAccuracy()
	{
		return m_ignoreAccuracy;
	}

	/**
	 * Set if the accuracy of a GPS fix should be ignored
	 * @param ignoreAcuracy the value	 */
	public void setIgnoreAccuracy(boolean ignoreAcuracy)
	{
		m_ignoreAccuracy = ignoreAcuracy;
	}

	/**
	 * Check if a location is available
	 * @return true if a location is available, false otherwise
	 */
	public boolean hasLocation() {
		return m_locationList.peek() != null;
	}

	/**
	 * Get the last location
	 * @return the last location null if no location is available
	 */
	public Location lastLocation()
	{
		return m_locationList.peek();
	}

	/**
	 * Get the number of locations in the current buffer
	 * @return the num of locations
	 */
	public int getNumLocations()
	{
		return m_locationList.size();
	}

	/**
	 * Get the resolution if the GPS receiver
	 * @return the resolution
	 */
	public double getResolution()
	{
		return m_resolution;
	}

	/**
	 * Process a new location
	 * @param newLocation the new location
	 * @return true if the location can be processed by the caller
	 */
	public boolean onLocationChanged( Location newLocation )
	{
		double	lastSpeed, elapsedTime;
		double	sDistance;

		m_accuracy = newLocation.getAccuracy();
		lastSpeed = 0;

		// calculate the current bearing
		{
			double sumBearing = 0;
			double minBearing = 1000;
			double maxBearing = -1000;
			int countPoints = 0;
			for( Location curLoc : m_locationList )
			{
				if( m_ignoreAccuracy || curLoc.distanceTo(newLocation) >= m_accuracy )
				{
					final double bearing = curLoc.bearingTo(newLocation);
					if( bearing < minBearing )
					{
						minBearing = bearing;
					}
					else if( bearing > maxBearing )
					{
						maxBearing = bearing;
					}
					sumBearing += bearing;
					countPoints++;
				}
			}
			sumBearing -= minBearing;
			sumBearing -= maxBearing;
			countPoints -= 2;
			if( countPoints > MIN_BEARING_COUNT )
			{
				m_curBearing = sumBearing / countPoints;

			}
		}

		// remove outdated way points
		Location speedLocation = m_locationList.peek();
		if( speedLocation != null )
		{
			long maxTime = newLocation.getTime() - MAX_AGE_MS;
			while( (
						speedLocation.distanceTo(newLocation) > m_accuracy*2
						|| speedLocation.getTime() < maxTime
					)
					&& m_locationList.size() > MIN_LOCATION_COUNT)
			{
				m_locationList.remove();
				Location tmpLocation = m_locationList.peek();
				if( tmpLocation == null )
					break;
				if( tmpLocation.distanceTo(newLocation) < m_accuracy )
					break;
				speedLocation = tmpLocation;
			}
		}

		// find the position to calculate the speed
		if( speedLocation != null )
		{
			long maxTime = newLocation.getTime() - 2000;
			speedLocation = null;
			double maxDistance = 0;
			for( Location curLoc : m_locationList )
			{
				if( curLoc.getTime() < maxTime )
				{
					break;
				}
				final double distance = curLoc.distanceTo(newLocation); 
				if( distance >= m_accuracy )
				{
					speedLocation = curLoc;
					break;
				}
				if( distance > maxDistance)
				{
					maxDistance = distance;
				}
			}
			if( speedLocation == null && m_ignoreAccuracy )
			{
				for( Location curLoc : m_locationList )
				{
					if( curLoc.distanceTo(newLocation) >= maxDistance )
					{
						speedLocation = curLoc;
						break;
					}
				}
			}
		}

		// calculate the current speed
		if( speedLocation != null )
		{
			sDistance = speedLocation.distanceTo(newLocation);
			elapsedTime = (newLocation.getTime() - speedLocation.getTime())/1000;
			lastSpeed = speedLocation.getSpeed();
		}
		else
		{
			sDistance = 0;
			elapsedTime = 0;
		}

		double speed, accel;

		if( elapsedTime > 0 && m_resolution > elapsedTime)
		{
			m_resolution = elapsedTime;
		}
		if( elapsedTime > 0 && (m_ignoreAccuracy || sDistance >= m_accuracy) )
		{
			char localSeparator = DecimalFormatSymbols.getInstance().getDecimalSeparator();
			speed = sDistance / elapsedTime;
			accel = (speed - lastSpeed)/elapsedTime;
			m_accelStr = ">" + accel + "=" + lastSpeed + "-" + speed + "/" + elapsedTime;
			m_accelStr = m_accelStr
				.replace('.', localSeparator)
				.replace(',', localSeparator);
		}
		else if( newLocation.hasSpeed() )
		{
			speed = newLocation.getSpeed();
			if(elapsedTime>0)
			{
				char localSeparator = DecimalFormatSymbols.getInstance().getDecimalSeparator();
				accel = (speed - lastSpeed)/elapsedTime;
				m_accelStr = ">" + accel + "=" + lastSpeed + "->" + speed + "/" + elapsedTime;
				m_accelStr = m_accelStr
					.replace('.', localSeparator)
					.replace(',', localSeparator);
			}
			else
			{
				m_accelStr = "no time1_" + m_locationList.size();
				accel = 0;
			}
		}
		else
		{
			speed = 0;
			accel = 0;
			m_accelStr = "no time2_" + m_locationList.size();
		}

		if ( (accel < 200 && accel > -200) )
		{
			m_speed = speed;
			m_accel = accel;
			newLocation.setSpeed((float)speed);
			m_locationList.add(newLocation);

			if( m_speed < 1 )
			{
				if( m_startBrake == null )
				{
					m_startBrake = speedLocation;
				}
			}
			else if( m_startBrake != null )
			{
				m_brakeTime += newLocation.getTime()-m_startBrake.getTime();
				m_startBrake = null;
			}
			return true;
		}
		return false;
	}
}

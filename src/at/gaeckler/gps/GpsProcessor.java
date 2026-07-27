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

import java.util.LinkedList;
import java.util.Queue;

import android.location.Location;

public class GpsProcessor
{
	static final int MAX_AGE_MS = 5000;
	static final int MIN_BEARING_COUNT = 2;
	static final int MIN_LOCATION_COUNT = 20;

	boolean					m_ignoreAccuracy = false;
	double					m_accuracy = 0.0;
	Queue<Location>			m_locationList = new LinkedList<Location>();
	long					m_breakTime = 0;
	Location				m_startBreak = null;
	double					m_curBearing = 0;
	double					m_speed = 0;
	double					m_accel = 0;
	String					m_accelStr = "";
	double					m_resolution = 99999999;

	public static long speedToKmh( double speedMs )
	{
		return (long)(speedMs * 3.6 + 0.5);
	}
	public static double speedToMs( long speedKmh )
	{
		return (double)speedKmh / 3.6;
	}

	public long getBreakTime()
	{
		return m_breakTime + (
			m_startBreak != null ? lastLocation().getTime()-m_startBreak.getTime() 
								 :0);
	}
	public void setBreakTime( long breakTime )
	{
		m_breakTime = breakTime;
		m_startBreak = null;
	}
	public double getCurBearing()
	{
		return m_curBearing;
	}
	public double getSpeed()
	{
		return m_speed;
	}
	public double getAccel()
	{
		return m_accel;
	}
	public String getAccelStr()
	{
		return m_accelStr;
	}
	public double getAccuracy()
	{
		return m_accuracy;
	}
	public boolean getIgnoreAccuracy()
	{
		return m_ignoreAccuracy;
	}
	public void setIgnoreAccuracy(boolean ignoreAcuracy)
	{
		m_ignoreAccuracy = ignoreAcuracy;
	}
	public boolean hasLocation() {
		return m_locationList.peek() != null;
	}
	public Location lastLocation()
	{
		return m_locationList.peek();
	}
	public int getNumLocations()
	{
		return m_locationList.size();
	}
	public double getResolution()
	{
		return m_resolution;
	}

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
    		speed = sDistance / elapsedTime; 
    		accel = (speed - lastSpeed)/elapsedTime;
    		m_accelStr = ">" + Double.toString(accel) + "=" + 
    		Double.toString(lastSpeed) + "-" + Double.toString(speed) + "/" + 
    				Double.toString(elapsedTime);  
    	}
    	else if( newLocation.hasSpeed() )
    	{
    		speed = newLocation.getSpeed();
    		if(elapsedTime>0)
    		{
    			accel = (speed - lastSpeed)/elapsedTime;
        		m_accelStr = ">" + Double.toString(accel) + "=" + 
    			Double.toString(lastSpeed) + "->" + Double.toString(speed) + "/" + 
        				Double.toString(elapsedTime);  
    		}
    		else
    		{
        		m_accelStr = "no time1_" + Integer.toString(m_locationList.size());  
    			accel = 0;
    		}
    	}
    	else
    	{
    		speed = 0;
    		accel = 0;
    		m_accelStr = "no time2_" + Integer.toString(m_locationList.size());
    	}

    	if ( (accel < 200 && accel > -200) )
    	{
    		m_speed = speed;
    		m_accel = accel;
    		newLocation.setSpeed((float)speed);
    		m_locationList.add(newLocation);
    		
    		if( m_speed < 1 )
    		{
    			if( m_startBreak == null )
    			{
    				m_startBreak = speedLocation;
    			}
    		}
    		else if( m_startBreak != null ) 
    		{
    			m_breakTime += newLocation.getTime()-m_startBreak.getTime();
    			m_startBreak = null;
    		}
    		return true;
    	}
    	return false;
    }

}

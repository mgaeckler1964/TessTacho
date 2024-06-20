package at.gaeckler.gps;

import java.util.LinkedList;
import java.util.Queue;

import android.location.Location;

public class GpsProcessor {

	static final int MAX_AGE_MS = 5000;
	static final int MIN_BEARING_COUNT = 2;
	static final int MIN_LOCATION_COUNT = 20;

	double					m_accuracy = 0.0;
	Queue<Location>			m_locationList = new LinkedList<Location>();
	double					m_curBearing = 0;
	double					m_speed = 0;
	double					m_accel = 0;
	double					m_resolution = 99999999;

	public static long speedToKmh( double speedMs )
	{
		return (long)(speedMs * 3.6 + 0.5);
	}
	public static double speedToMs( long speedKmh )
	{
		return (double)speedKmh / 3.6;
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
	public double getAccuracy()
	{
		return m_accuracy;
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
	    		if( curLoc.distanceTo(newLocation) >= m_accuracy )
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
    		while( (speedLocation.distanceTo(newLocation) > m_accuracy*2 || speedLocation.getTime() < maxTime) 
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
			for( Location curLoc : m_locationList )
			{
				if( curLoc.getTime() < maxTime )
				{
					break;
				}
	    		if( curLoc.distanceTo(newLocation) > m_accuracy )
	    		{
	    			speedLocation = curLoc;
	    			break;
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
    	
    	if( elapsedTime > 0 && m_resolution >elapsedTime)
    	{
    		m_resolution = elapsedTime;
    	}
    	if( elapsedTime > 0 && sDistance >= m_accuracy )
    	{
    		speed = sDistance / elapsedTime; 
    		accel = (speed - lastSpeed)/elapsedTime;
    	}
    	else if( newLocation.hasSpeed() )
    	{
    		speed = newLocation.getSpeed();
    		if(elapsedTime>0)
    		{
    			accel = (speed - lastSpeed)/elapsedTime;
    		}
    		else
    		{
    			accel = 0;
    		}
    	}
    	else
    	{
    		speed = 0;
    		accel = 0;
    	}

    	if ( accel < 200 && accel > -200 )
    	{
    		m_speed = speed;
    		m_accel = accel;
    		newLocation.setSpeed((float)speed);
    		m_locationList.add(newLocation);
    		
    		return true;
    	}
    	return false;
    }

}

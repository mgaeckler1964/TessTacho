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

	public void onLocationChanged( Location newLocation )
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
			elapsedTime = newLocation.getTime() - speedLocation.getTime();
			elapsedTime /= 1000;
	    }
    	else
    	{
    		sDistance = 0;
    		elapsedTime = 0;
    	}
    	
    	if( elapsedTime > 0 && sDistance >= m_accuracy )
    	{
    		m_speed = sDistance / elapsedTime; 
    		m_accel = (m_speed - lastSpeed)/elapsedTime;
    	}
    	else if( newLocation.hasSpeed() )
    		m_speed = newLocation.getSpeed();
    	else
    		m_speed = 0;

    	newLocation.setSpeed((float)m_speed);
    	m_locationList.add(newLocation);
    }

}

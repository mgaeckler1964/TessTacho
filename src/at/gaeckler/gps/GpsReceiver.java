/*
		Project:		GPS
		Module:			GpsReceiver.java
		Description:	Get GPS Positions from any source,the GpsReceiver
						tries to remove noise from the GPS-signal
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
import android.location.LocationManager;

import androidx.annotation.NonNull;

import java.util.concurrent.locks.ReentrantLock;

public class GpsReceiver
{
	private static final double	MAX_SPEED = 100;
	private static final double	MAX_ACCEL = 100;
	private final ReentrantLock m_lock = new ReentrantLock();
	private long					m_locationFixCount = 0;
	private Location[]				m_lastLocations;
	private boolean					m_goodGps = false;
	private long					m_startTime = 0;
	private final GpsProcessor 		m_processor;
	private final GpsLogger 		m_gpsLogger;

	GpsReceiver( GpsProcessor processor, GpsLogger logger )
	{
		m_processor = processor;
		m_gpsLogger = logger;
	}

	public long getLocationFixCount()
	{
		return m_locationFixCount;
	}

	public interface GpsCallback
	{
		void onGpsFix(Location loc);
	}

	public void lockLocationChanged(@NonNull Location newLocation, boolean fromGPS, final GpsCallback callback )
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
					m_gpsLogger.appendTrackPoint(newLocation);
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
							double speed = GpsUtils.getSpeed(m_lastLocations[0],newLocation);
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
						double speed = GpsUtils.getSpeed(m_lastLocations[1],newLocation);
						newLocation.setSpeed((float) speed);
						double accel = GpsUtils.getAccel(m_lastLocations[1], newLocation );

						// try to filter GPS-noise
						if(speed < MAX_SPEED && accel < MAX_ACCEL )
						{
							m_lastLocations[0] = m_lastLocations[1];
							m_lastLocations[1] = newLocation;
							m_goodGps = true;
							if( m_processor.onLocationChanged(newLocation) )
							{
								callback.onGpsFix(newLocation);
								if( m_gpsLogger.getTrackGps())
									m_gpsLogger.appendTrackPoint2XML(newLocation);
							}
						}
					}
				}
			}
			finally
			{
				m_lock.unlock();
			}
		}
	}
}
